package com.littlekrishnaa.barsplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlekrishnaa.barsplayer.data.local.entity.TrackEntity
import com.littlekrishnaa.barsplayer.data.model.LyricsTranslationResponse
import com.littlekrishnaa.barsplayer.data.repository.LibraryRepository
import com.littlekrishnaa.barsplayer.lyrics.LyricsPipelineRepository
import com.littlekrishnaa.barsplayer.lyrics.LyricsSettingsManager
import com.littlekrishnaa.barsplayer.lyrics.SyncedLine
import com.littlekrishnaa.barsplayer.playback.AudioOutputInfo
import com.littlekrishnaa.barsplayer.playback.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val lyricsPipeline: LyricsPipelineRepository,
    val settingsManager: LyricsSettingsManager,
    val playerController: MusicPlayerController
) : ViewModel() {

    val tracks: StateFlow<List<TrackEntity>> = libraryRepository.allTracks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Mirror player states from MusicPlayerController
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val currentTrackId: StateFlow<String?> = playerController.currentTrackId
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val shuffleEnabled: StateFlow<Boolean> = playerController.shuffleEnabled

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _outputInfo = MutableStateFlow(AudioOutputInfo())
    val outputInfo: StateFlow<AudioOutputInfo> = _outputInfo.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<SyncedLine>>(emptyList())
    val syncedLyrics: StateFlow<List<SyncedLine>> = _syncedLyrics.asStateFlow()

    private val _translatedLyrics = MutableStateFlow<LyricsTranslationResponse?>(null)
    val translatedLyrics: StateFlow<LyricsTranslationResponse?> = _translatedLyrics.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    // Active synced line index berdasarkan posisi playback
    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    init {
        playerController.connect()
        scanLibrary()
        startPositionPoller()
    }

    fun scanLibrary() {
        viewModelScope.launch {
            libraryRepository.scanLocalAudioFiles()
        }
    }

    fun selectTrack(track: TrackEntity) {
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _activeLyricIndex.value = -1

        _outputInfo.value = AudioOutputInfo(
            sampleRate = track.sampleRate,
            bitDepth = track.bitDepth,
            channelCount = 2,
            encoding = "PCM_${track.bitDepth}-bit",
            isBitPerfect = false, // akan diupdate oleh BitPerfectAudioSink
            outputDeviceName = "Connecting..."
        )

        playerController.playTrack(track)
        loadLyricsForTrack(track)
    }

    fun playAllFromIndex(tracks: List<TrackEntity>, startIndex: Int) {
        if (tracks.isEmpty()) return
        _currentTrack.value = tracks[startIndex]
        _currentPositionMs.value = 0L
        _activeLyricIndex.value = -1
        playerController.playQueue(tracks, startIndex)
        loadLyricsForTrack(tracks[startIndex])
    }

    fun togglePlayPause() = playerController.togglePlayPause()

    fun skipNext() {
        playerController.skipNext()
    }

    fun skipPrevious() {
        playerController.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun retranslate() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            val cacheKey = generateCacheKey(track)
            // Hapus cache lama agar LyricsPipelineRepository fetch ulang
            _translatedLyrics.value = null
            loadLyricsForTrack(track)
        }
    }

    private fun startPositionPoller() {
        viewModelScope.launch {
            while (isActive) {
                if (playerController.isPlaying.value) {
                    val pos = playerController.getCurrentPosition()
                    _currentPositionMs.value = pos
                    _durationMs.value = playerController.getDuration()
                    updateActiveLyricIndex(pos)
                }
                delay(200L)
            }
        }
    }

    private fun updateActiveLyricIndex(positionMs: Long) {
        val lines = _syncedLyrics.value
        if (lines.isEmpty()) return
        val idx = lines.indexOfLast { it.timeMs <= positionMs }
        if (idx != _activeLyricIndex.value) {
            _activeLyricIndex.value = idx
        }
    }

    private fun loadLyricsForTrack(track: TrackEntity) {
        viewModelScope.launch {
            _isTranslating.value = true
            val (lines, translation) = lyricsPipeline.getOrFetchLyrics(
                audioFilePath = track.path,
                artist = track.artist,
                title = track.title,
                durationSec = (track.durationMs / 1000).toInt()
            )
            _syncedLyrics.value = lines
            _translatedLyrics.value = translation
            _isTranslating.value = false
        }
    }

    private fun generateCacheKey(track: TrackEntity): String = "${track.artist}-${track.title}"

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
