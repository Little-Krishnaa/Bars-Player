package com.littlekrishnaa.barsplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.littlekrishnaa.barsplayer.data.local.entity.TrackEntity
import com.littlekrishnaa.barsplayer.lyrics.LyricsSettingsManager

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Now Playing") },
                    label = { Text("Playing") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> LibraryScreen(viewModel = viewModel, onTrackSelected = {
                    viewModel.selectTrack(it)
                    selectedTab = 1
                })
                1 -> PlayerLyricsScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LIBRARY SCREEN
// ─────────────────────────────────────────────────────────────
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onTrackSelected: (TrackEntity) -> Unit
) {
    val tracks by viewModel.tracks.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bars Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row {
                if (tracks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.playAllFromIndex(tracks, 0) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = Color(0xFF38BDF8))
                    }
                }
                IconButton(onClick = { viewModel.scanLibrary() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("${tracks.size} lossless tracks", color = Color.Gray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No lossless audio found.\nPlace FLAC/WAV files on device storage.",
                    color = Color.Gray,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        onClick = { onTrackSelected(track) }
                    )
                    HorizontalDivider(color = Color(0xFF1E293B))
                }
            }
        }
    }
}

@Composable
fun TrackItem(track: TrackEntity, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color(0xFF0F2A3F) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPlaying) {
                Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Playing",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) Color(0xFF38BDF8) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
            Text(
                text = "${track.sampleRate / 1000}kHz/${track.bitDepth}b",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                color = Color(0xFF38BDF8)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PLAYER + LYRICS SCREEN
// ─────────────────────────────────────────────────────────────
@Composable
fun PlayerLyricsScreen(viewModel: MainViewModel) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val outputInfo by viewModel.outputInfo.collectAsState()
    val syncedLines by viewModel.syncedLyrics.collectAsState()
    val translatedLyrics by viewModel.translatedLyrics.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll ke lirik aktif
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && syncedLines.isNotEmpty()) {
            listState.animateScrollToItem(activeLyricIndex.coerceAtMost(syncedLines.lastIndex))
        }
    }

    if (track == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a track from Library", color = Color.Gray)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Bit-Perfect & DAC Indicator
        Surface(
            color = if (outputInfo.isBitPerfect) Color(0xFF064E3B) else Color(0xFF1F2937),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (outputInfo.isBitPerfect) "● BIT-PERFECT DIRECT" else "○ Standard Output",
                        color = if (outputInfo.isBitPerfect) Color(0xFF34D399) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${outputInfo.outputDeviceName} • ${outputInfo.sampleRate}Hz / ${outputInfo.bitDepth}-bit",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Track Info
        Text(track!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${track!!.artist} — ${track!!.album}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(modifier = Modifier.height(10.dp))

        // Lyrics Area
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color(0xFF0A1628),
            shape = RoundedCornerShape(12.dp)
        ) {
            when {
                isTranslating -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Translating lyrics via LLM...", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
                syncedLines.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No synced lyrics available", color = Color.Gray)
                    }
                }
                else -> {
                    val translatedMap = translatedLyrics?.lines?.associateBy { it.timeMs } ?: emptyMap()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        items(syncedLines.size) { index ->
                            val line = syncedLines[index]
                            val trans = translatedMap[line.timeMs]
                            val isActive = index == activeLyricIndex

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) Color(0xFF1E3A5F) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = if (isActive) 8.dp else 0.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    color = if (isActive) Color.White else Color(0xFF94A3B8),
                                    fontSize = if (isActive) 17.sp else 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                if (trans != null && trans.translated.isNotBlank()) {
                                    Text(
                                        text = trans.translated,
                                        color = if (isActive) Color(0xFF38BDF8) else Color(0xFF334B6E),
                                        fontSize = if (isActive) 15.sp else 13.sp
                                    )
                                    trans.note?.let { note ->
                                        Text(
                                            text = "💡 $note",
                                            color = Color(0xFFFBBF24),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Seekbar
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        Slider(
            value = progress,
            onValueChange = { viewModel.seekTo((it * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = Color(0xFF1E293B)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(positionMs), fontSize = 11.sp, color = Color.Gray)
            Text(formatMs(durationMs), fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Shuffle & Repeat
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) Color(0xFF38BDF8) else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF2563EB), RoundedCornerShape(30.dp))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                Icon(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Color.Gray
                        else -> Color(0xFF38BDF8)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

// ─────────────────────────────────────────────────────────────
// SETTINGS SCREEN
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings = viewModel.settingsManager
    var mode by remember { mutableIntStateOf(settings.providerMode) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    var endpoint by remember { mutableStateOf(settings.customEndpoint) }
    var personaPrompt by remember { mutableStateOf(settings.personaPrompt) }

    val presets = listOf(
        "Hip-Hop" to LyricsSettingsManager.DEFAULT_HIPHOP_PRESET.trim(),
        "Pop" to "Translate naturally and cheerfully. Preserve rhyme and rhythm where possible. Keep the emotional tone light and accessible.",
        "R&B / Soul" to "Translate the emotional depth and intimacy of the lyrics. Focus on feeling, longing, and personal expression. Tone should feel warm and vulnerable.",
        "Rock" to "Capture the raw energy and attitude of the lyrics. Prioritize intensity and directness over poetic nuance.",
        "Custom" to personaPrompt
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("AI Translation Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Provider Mode
        Text("Provider", fontWeight = FontWeight.SemiBold, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "OpenAI" to LyricsSettingsManager.MODE_OPENAI,
                "Anthropic" to LyricsSettingsManager.MODE_ANTHROPIC,
                "Custom" to LyricsSettingsManager.MODE_CUSTOM_HTTP
            ).forEach { (label, value) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { mode = value; settings.providerMode = value },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mode == LyricsSettingsManager.MODE_CUSTOM_HTTP) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; settings.customEndpoint = it },
                label = { Text("Endpoint URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; settings.apiKey = it },
            label = { Text("API Key (encrypted)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it; settings.modelName = it },
            label = { Text("Model (e.g. gpt-4o-mini, claude-3-5-haiku)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Genre Preset
        Text("Translation Style Preset", fontWeight = FontWeight.SemiBold, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.dropLast(1).forEach { (label, prompt) ->
                FilterChip(
                    selected = personaPrompt.trim() == prompt.trim(),
                    onClick = {
                        personaPrompt = prompt
                        settings.personaPrompt = prompt
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = personaPrompt,
            onValueChange = { personaPrompt = it; settings.personaPrompt = it },
            label = { Text("Persona / Style Prompt") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxLines = 10
        )
    }
}
