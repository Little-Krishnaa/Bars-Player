package com.littlekrishnaa.barsplayer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlekrishnaa.barsplayer.data.local.dao.PlaylistDao
import com.littlekrishnaa.barsplayer.data.local.dao.TrackDao
import com.littlekrishnaa.barsplayer.data.local.entity.PlaylistEntity
import com.littlekrishnaa.barsplayer.data.local.entity.PlaylistTrackEntity
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AlbumItem(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val sampleTrackPath: String
)

data class ArtistItem(
    val name: String,
    val trackCount: Int,
    val sampleTrackPath: String
)

data class FolderItem(
    val path: String,
    val name: String,
    val trackCount: Int
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val lyricsPipeline: LyricsPipelineRepository,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    val settingsManager: LyricsSettingsManager,
    val playerController: MusicPlayerController
) : ViewModel() {

    val tracks: StateFlow<List<TrackEntity>> = libraryRepository.allTracks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val searchQuery = MutableStateFlow("")

    val filteredTracks: StateFlow<List<TrackEntity>> = combine(tracks, searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.album.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumItem>> = tracks.map { list ->
        list.groupBy { it.album.ifBlank { "Unknown Album" } }
            .map { (albumName, albumTracks) ->
                AlbumItem(
                    name = albumName,
                    artist = albumTracks.firstOrNull()?.artist ?: "Unknown Artist",
                    trackCount = albumTracks.size,
                    sampleTrackPath = albumTracks.firstOrNull()?.path ?: ""
                )
            }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<ArtistItem>> = tracks.map { list ->
        list.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .map { (artistName, artistTracks) ->
                ArtistItem(
                    name = artistName,
                    trackCount = artistTracks.size,
                    sampleTrackPath = artistTracks.firstOrNull()?.path ?: ""
                )
            }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderItem>> = tracks.map { list ->
        list.groupBy {
            val p = it.path
            if (p.startsWith("content://")) {
                Uri.parse(p).lastPathSegment?.substringBeforeLast('/') ?: "Storage"
            } else {
                File(p).parent ?: "Storage"
            }
        }.map { (folderPath, folderTracks) ->
            val folderName = folderPath.substringAfterLast('/')
            FolderItem(
                path = folderPath,
                name = folderName.ifEmpty { "Music" },
                trackCount = folderTracks.size
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<TrackEntity>> = trackDao.getFavoriteTracks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getAllPlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Navigation selections for drill-down views
    val selectedAlbum = MutableStateFlow<AlbumItem?>(null)
    val selectedArtist = MutableStateFlow<ArtistItem?>(null)
    val selectedFolder = MutableStateFlow<FolderItem?>(null)
    val selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)

    // Full screen player visibility toggle (ala Spotify modal)
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    fun expandPlayer() {
        if (_currentTrack.value != null) {
            _isPlayerExpanded.value = true
        }
    }

    fun collapsePlayer() {
        _isPlayerExpanded.value = false
    }

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

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _savedFolders = MutableStateFlow(libraryRepository.getSavedFolderUris())
    val savedFolders: StateFlow<Set<String>> = _savedFolders.asStateFlow()

    init {
        playerController.connect()
        scanLibrary()
        startPositionPoller()
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            libraryRepository.scanAll()
            _isScanning.value = false
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            libraryRepository.saveFolderUri(uri)
            libraryRepository.scanDocumentFolder(uri)
            _savedFolders.value = libraryRepository.getSavedFolderUris()
            _isScanning.value = false
        }
    }

    fun removeFolder(uriString: String) {
        libraryRepository.removeFolderUri(uriString)
        _savedFolders.value = libraryRepository.getSavedFolderUris()
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
            isBitPerfect = false,
            outputDeviceName = "Connecting..."
        )

        playerController.playTrack(track)
        loadLyricsForTrack(track)
    }

    fun playAllFromIndex(trackList: List<TrackEntity>, startIndex: Int) {
        if (trackList.isEmpty()) return
        val track = trackList[startIndex]
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _activeLyricIndex.value = -1

        _outputInfo.value = AudioOutputInfo(
            sampleRate = track.sampleRate,
            bitDepth = track.bitDepth,
            channelCount = 2,
            encoding = "PCM_${track.bitDepth}-bit",
            isBitPerfect = false,
            outputDeviceName = "Connecting..."
        )

        playerController.playQueue(trackList, startIndex)
        loadLyricsForTrack(track)
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

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            val newFav = !track.isFavorite
            trackDao.setFavorite(track.id, newFav)
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = _currentTrack.value?.copy(isFavorite = newFav)
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(PlaylistEntity(name = name.trim()))
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist)
            if (selectedPlaylist.value?.id == playlist.id) {
                selectedPlaylist.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistDao.addTrackToPlaylist(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId
                )
            )
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    fun retranslate() {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            _translatedLyrics.value = null
            loadLyricsForTrack(track)
        }
    }

    fun searchManualLyrics(query: String) {
        val track = _currentTrack.value ?: return
        viewModelScope.launch {
            _isSearchingLyrics.value = true
            val (lines, translation) = lyricsPipeline.searchManualLyrics(query, track.artist, track.title)
            if (lines.isNotEmpty()) {
                _syncedLyrics.value = lines
                _translatedLyrics.value = translation
            }
            _isSearchingLyrics.value = false
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

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
