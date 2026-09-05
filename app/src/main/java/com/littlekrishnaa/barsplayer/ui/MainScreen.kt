package com.littlekrishnaa.barsplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.littlekrishnaa.barsplayer.data.local.entity.PlaylistEntity
import com.littlekrishnaa.barsplayer.data.local.entity.TrackEntity
import com.littlekrishnaa.barsplayer.lyrics.LyricsSettingsManager
import com.littlekrishnaa.barsplayer.ui.common.rememberCoverArt

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()

    // Jika fullscreen player aktif, tekan tombol back akan menutupnya
    BackHandler(enabled = isPlayerExpanded) {
        viewModel.collapsePlayer()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090D16))) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Floating Mini Player ala Spotify
                    if (currentTrack != null && !isPlayerExpanded) {
                        FloatingMiniPlayer(
                            viewModel = viewModel,
                            track = currentTrack!!,
                            onClick = { viewModel.expandPlayer() }
                        )
                    }

                    NavigationBar(
                        containerColor = Color(0xFF0F172A),
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF38BDF8),
                                selectedTextColor = Color(0xFF38BDF8),
                                indicatorColor = Color(0xFF1E293B),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = {
                                if (currentTrack != null) {
                                    viewModel.expandPlayer()
                                } else {
                                    selectedTab = 1
                                }
                            },
                            icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Now Playing") },
                            label = { Text("Playing") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF38BDF8),
                                selectedTextColor = Color(0xFF38BDF8),
                                indicatorColor = Color(0xFF1E293B),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF38BDF8),
                                selectedTextColor = Color(0xFF38BDF8),
                                indicatorColor = Color(0xFF1E293B),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> LibraryScreen(viewModel = viewModel)
                    1 -> {
                        if (currentTrack != null) {
                            ProPlayerLyricsScreen(viewModel = viewModel, isModal = false)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Pilih lagu dari Library untuk mulai memutar", color = Color.Gray)
                            }
                        }
                    }
                    2 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }

        // Fullscreen Modal Player ala Apple Music / Spotify
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090D16))
            ) {
                ProPlayerLyricsScreen(viewModel = viewModel, isModal = true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// FLOATING MINI PLAYER
// ─────────────────────────────────────────────────────────────
@Composable
fun FloatingMiniPlayer(
    viewModel: MainViewModel,
    track: TrackEntity,
    onClick: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val coverBitmap = rememberCoverArt(track.path)

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    Surface(
        color = Color(0xFF1E293B).copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Art Thumbnail
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${track.bitDepth}b/${track.sampleRate / 1000}k",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Play / Pause
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Skip Next
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Mini progress bar on bottom edge
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFF38BDF8),
                trackColor = Color(0xFF0F172A)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PRO LIBRARY SCREEN (TABS: TRACKS / ALBUMS / ARTISTS / FOLDERS / PLAYLISTS / FAVORITES)
// ─────────────────────────────────────────────────────────────
@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tracks by viewModel.filteredTracks.collectAsState()
    val allTracks by viewModel.tracks.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val favorites by viewModel.favoriteTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filterLabels = listOf("Semua Lagu", "Album", "Artis", "Folder", "Playlist", "Favorit ♥")

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<TrackEntity?>(null) }

    val primaryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, primaryPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioGranted = result[primaryPermission] ?: (ContextCompat.checkSelfPermission(context, primaryPermission) == PackageManager.PERMISSION_GRANTED)
        hasPermission = audioGranted
        if (audioGranted) {
            viewModel.scanLibrary()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addFolder(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Detail view handlers
    if (selectedAlbum != null) {
        AlbumDetailView(
            album = selectedAlbum!!,
            allTracks = allTracks,
            viewModel = viewModel,
            onBack = { viewModel.selectedAlbum.value = null }
        )
        return
    }

    if (selectedArtist != null) {
        ArtistDetailView(
            artist = selectedArtist!!,
            allTracks = allTracks,
            viewModel = viewModel,
            onBack = { viewModel.selectedArtist.value = null }
        )
        return
    }

    if (selectedFolder != null) {
        FolderDetailView(
            folder = selectedFolder!!,
            allTracks = allTracks,
            viewModel = viewModel,
            onBack = { viewModel.selectedFolder.value = null }
        )
        return
    }

    if (selectedPlaylist != null) {
        PlaylistDetailView(
            playlist = selectedPlaylist!!,
            viewModel = viewModel,
            onBack = { viewModel.selectedPlaylist.value = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BARS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF38BDF8),
                letterSpacing = 2.sp
            )

            Row {
                IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Pilih Folder", tint = Color(0xFF38BDF8))
                }
                IconButton(onClick = { viewModel.scanLibrary() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Pindai Ulang", tint = Color.White)
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Cari lagu, artis, atau album...", fontSize = 13.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.Gray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
            ),
            singleLine = true
        )

        // Filter Chips (Tracks, Albums, Artists, Folders, Playlists, Favorites)
        ScrollableTabRow(
            selectedTabIndex = selectedFilterIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF38BDF8),
            divider = {}
        ) {
            filterLabels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedFilterIndex == index,
                    onClick = { selectedFilterIndex = index },
                    text = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedFilterIndex == index) Color(0xFF38BDF8) else Color.Gray
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFF38BDF8),
                trackColor = Color(0xFF1E293B)
            )
            Text("Memindai library musik...", fontSize = 12.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Permission Banner jika belum diberikan
        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Akses Audio Belum Diizinkan", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Berikan izin agar Bars Player dapat memindai musik di perangkat.", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Izinkan Akses", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Text("Pilih Folder")
                        }
                    }
                }
            }
        }

        // Content based on selectedFilterIndex
        when (selectedFilterIndex) {
            0 -> { // Tracks
                if (tracks.isEmpty()) {
                    EmptyLibraryPlaceholder(
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        onRescan = { viewModel.scanLibrary() }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${tracks.size} lagu lossless", color = Color.Gray, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = { viewModel.playAllFromIndex(tracks, 0) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Putar Semua", color = Color(0xFF38BDF8), fontSize = 12.sp)
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tracks, key = { it.id }) { track ->
                            TrackRowItem(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { viewModel.selectTrack(track) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                            HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
                        }
                    }
                }
            }

            1 -> { // Albums
                if (albums.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada album terdeteksi", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums) { album ->
                            AlbumGridCard(
                                album = album,
                                onClick = { viewModel.selectedAlbum.value = album }
                            )
                        }
                    }
                }
            }

            2 -> { // Artists
                if (artists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada artis terdeteksi", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists) { artist ->
                            ArtistRowItem(
                                artist = artist,
                                onClick = { viewModel.selectedArtist.value = artist }
                            )
                            HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
                        }
                    }
                }
            }

            3 -> { // Folders
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${folders.size} folder musik", color = Color.Gray, fontSize = 12.sp)
                    TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Folder", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }

                if (folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada folder terindeks. Klik 'Tambah Folder' untuk memilih.", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(folders) { folder ->
                            FolderRowItem(
                                folder = folder,
                                onClick = { viewModel.selectedFolder.value = folder }
                            )
                            HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
                        }
                    }
                }
            }

            4 -> { // Playlists
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${playlists.size} playlist", color = Color.Gray, fontSize = 12.sp)
                    Button(
                        onClick = { showCreatePlaylistDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buat Playlist", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Pre-made Favorites item
                    item {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedFilterIndex = 5 }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFBE185D))), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Lagu Favorit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Text("${favorites.size} lagu tersimpan", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    items(playlists) { pl ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.selectedPlaylist.value = pl }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF38BDF8))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Text("Kustom Playlist", color = Color.Gray, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.deletePlaylist(pl) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            5 -> { // Favorites
                if (favorites.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada lagu favorit.\nKetuk ikon hati pada lagu untuk menyimpannya di sini.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${favorites.size} lagu favorit", color = Color.Gray, fontSize = 12.sp)
                        TextButton(onClick = { viewModel.playAllFromIndex(favorites, 0) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Putar Semua", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(favorites, key = { it.id }) { track ->
                            TrackRowItem(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { viewModel.selectTrack(track) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                            HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    // Dialog Tambah ke Playlist
    if (trackForPlaylist != null) {
        Dialog(onDismissRequest = { trackForPlaylist = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Tambahkan ke Playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(trackForPlaylist!!.title, color = Color(0xFF38BDF8), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (playlists.isEmpty()) {
                        Text("Belum ada playlist. Buat playlist baru terlebih dahulu.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(playlists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTrackToPlaylist(pl.id, trackForPlaylist!!.id)
                                            trackForPlaylist = null
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(pl.name, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { trackForPlaylist = null }) {
                            Text("Tutup", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }

    // Dialog Buat Playlist Baru
    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Buat Playlist Baru", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Nama Playlist") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("Batal", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (playlistName.isNotBlank()) {
                                    viewModel.createPlaylist(playlistName)
                                    showCreatePlaylistDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TRACK ROW ITEM
// ─────────────────────────────────────────────────────────────
@Composable
fun TrackRowItem(
    track: TrackEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val coverBitmap = rememberCoverArt(track.path)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color(0xFF0C243B) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Cover Art
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isPlaying) Color(0xFF38BDF8) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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

        // Hi-Res Badge
        Text(
            text = "${track.bitDepth}b/${track.sampleRate / 1000}k",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8),
            modifier = Modifier
                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        // Favorite Button
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorit",
                tint = if (track.isFavorite) Color(0xFFE11D48) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        // More / Add to Playlist
        IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Tambah ke Playlist", tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ALBUM GRID CARD
// ─────────────────────────────────────────────────────────────
@Composable
fun AlbumGridCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    val coverBitmap = rememberCoverArt(album.sampleTrackPath)

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Album, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(album.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${album.trackCount} lagu", color = Color(0xFF38BDF8), fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ARTIST ROW ITEM
// ─────────────────────────────────────────────────────────────
@Composable
fun ArtistRowItem(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Text("${artist.trackCount} lagu", color = Color.Gray, fontSize = 12.sp)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────
// FOLDER ROW ITEM
// ─────────────────────────────────────────────────────────────
@Composable
fun FolderRowItem(
    folder: FolderItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(folder.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(folder.path, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text("${folder.trackCount} lagu", color = Color(0xFF38BDF8), fontSize = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────
// ALBUM DETAIL VIEW
// ─────────────────────────────────────────────────────────────
@Composable
fun AlbumDetailView(
    album: AlbumItem,
    allTracks: List<TrackEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val albumTracks = remember(album, allTracks) {
        allTracks.filter { it.album == album.name }
    }
    val coverBitmap = rememberCoverArt(album.sampleTrackPath)
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(110.dp).background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Album, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(54.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(album.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = Color.Gray, fontSize = 14.sp)
                Text("${albumTracks.size} lagu lossless", color = Color(0xFF38BDF8), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.playAllFromIndex(albumTracks, 0) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Putar Album", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFF1E293B))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(albumTracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onClick = { viewModel.selectTrack(track) },
                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                    onAddToPlaylist = {}
                )
                HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ARTIST DETAIL VIEW
// ─────────────────────────────────────────────────────────────
@Composable
fun ArtistDetailView(
    artist: ArtistItem,
    allTracks: List<TrackEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val artistTracks = remember(artist, allTracks) {
        allTracks.filter { it.artist == artist.name }
    }
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(artist.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                Text("${artistTracks.size} lagu", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.playAllFromIndex(artistTracks, 0) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Putar Semua Lagu Artis", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(artistTracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onClick = { viewModel.selectTrack(track) },
                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                    onAddToPlaylist = {}
                )
                HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// FOLDER DETAIL VIEW
// ─────────────────────────────────────────────────────────────
@Composable
fun FolderDetailView(
    folder: FolderItem,
    allTracks: List<TrackEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val folderTracks = remember(folder, allTracks) {
        allTracks.filter { it.path.contains(folder.path) || it.path.substringBeforeLast('/').endsWith(folder.name) }
    }
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(folder.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Text(folder.path, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.playAllFromIndex(folderTracks, 0) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Putar Semua di Folder", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folderTracks, key = { it.id }) { track ->
                TrackRowItem(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onClick = { viewModel.selectTrack(track) },
                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                    onAddToPlaylist = {}
                )
                HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PLAYLIST DETAIL VIEW
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistDetailView(
    playlist: PlaylistEntity,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val playlistTracks by viewModel.getTracksForPlaylist(playlist.id).collectAsState(initial = emptyList())
    val currentTrackId by viewModel.currentTrackId.collectAsState()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(playlist.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Text("${playlistTracks.size} lagu tersimpan", color = Color.Gray, fontSize = 12.sp)
                }
            }

            IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Playlist", tint = Color.Red.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlistTracks.isNotEmpty()) {
            Button(
                onClick = { viewModel.playAllFromIndex(playlistTracks, 0) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Putar Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (playlistTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Playlist kosong.\nTambahkan lagu ke playlist dari daftar lagu.", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlistTracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRowItem(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { viewModel.selectTrack(track) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onAddToPlaylist = {}
                            )
                        }
                        IconButton(onClick = { viewModel.removeTrackFromPlaylist(playlist.id, track.id) }) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Hapus", tint = Color.Gray)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PRO PLAYER + KARAOKE LYRICS SCREEN (APPLE MUSIC / SPOTIFY STYLE)
// ─────────────────────────────────────────────────────────────
@Composable
fun ProPlayerLyricsScreen(
    viewModel: MainViewModel,
    isModal: Boolean = false
) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val outputInfo by viewModel.outputInfo.collectAsState()
    val syncedLines by viewModel.syncedLyrics.collectAsState()
    val translatedLyrics by viewModel.translatedLyrics.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val isSearchingLyrics by viewModel.isSearchingLyrics.collectAsState()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()

    var showLyricsMode by remember { mutableStateOf(false) }
    var showManualSearchDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(activeLyricIndex) {
        if (showLyricsMode && activeLyricIndex >= 0 && syncedLines.isNotEmpty()) {
            listState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    if (track == null) return

    val coverBitmap = rememberCoverArt(track!!.path)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isModal) {
                IconButton(onClick = { viewModel.collapsePlayer() }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }

            // Bit-Perfect / DAC Status Pill
            Surface(
                color = if (outputInfo.isBitPerfect) Color(0xFF064E3B) else Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (outputInfo.isBitPerfect) "● BIT-PERFECT" else "○ DIRECT",
                        color = if (outputInfo.isBitPerfect) Color(0xFF34D399) else Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${track!!.bitDepth}b / ${track!!.sampleRate / 1000}kHz",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Lyrics Toggle Button (Lyrics vs Cover Art)
            IconButton(onClick = { showLyricsMode = !showLyricsMode }) {
                Icon(
                    if (showLyricsMode) Icons.Default.MusicNote else Icons.Default.FormatQuote,
                    contentDescription = "Toggle Lirik",
                    tint = if (showLyricsMode) Color(0xFF38BDF8) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!showLyricsMode) {
            // ──────────────────────────────────────────
            // COVER ART VIEW (Large Square Album Art)
            // ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .shadow(16.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = Color(0xFF38BDF8).copy(alpha = 0.5f), modifier = Modifier.size(96.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Titles + Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track!!.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track!!.artist} — ${track!!.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.toggleFavorite(track!!) }) {
                    Icon(
                        if (track!!.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (track!!.isFavorite) Color(0xFFE11D48) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Quick lyrics preview banner
            val activeLine = syncedLines.getOrNull(activeLyricIndex)
            if (activeLine != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLyricsMode = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeLine.text,
                            fontSize = 13.sp,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }

        } else {
            // ──────────────────────────────────────────
            // KARAOKE SYNCED LYRICS VIEW (APPLE MUSIC STYLE)
            // ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track!!.title,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track!!.artist,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = { viewModel.retranslate() }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Re-translate AI", tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { showManualSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Cari Lirik Online", tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    when {
                        isTranslating || isSearchingLyrics -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        if (isSearchingLyrics) "Mencari lirik di LRCLIB..." else "Menerjemahkan dengan AI...",
                                        color = Color.LightGray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        syncedLines.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(Icons.Default.Lyrics, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Lirik sinkron belum ditemukan", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Pastikan file .lrc ada di samping audio atau gunakan pencarian manual online di bawah.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showManualSearchDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cari Lirik Online", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        else -> {
                            val translatedMap = translatedLyrics?.lines?.associateBy { it.timeMs } ?: emptyMap()

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                items(syncedLines.size) { index ->
                                    val line = syncedLines[index]
                                    val trans = translatedMap[line.timeMs]
                                    val isActive = index == activeLyricIndex

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.seekTo(line.timeMs) }
                                            .padding(vertical = if (isActive) 12.dp else 6.dp)
                                    ) {
                                        // Baris Asli
                                        Text(
                                            text = line.text,
                                            color = if (isActive) Color.White else Color.Gray.copy(alpha = 0.5f),
                                            fontSize = if (isActive) 21.sp else 16.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            lineHeight = if (isActive) 28.sp else 22.sp
                                        )

                                        // Baris Terjemahan LLM
                                        if (trans != null && trans.translated.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = trans.translated,
                                                color = if (isActive) Color(0xFF38BDF8) else Color(0xFF38BDF8).copy(alpha = 0.35f),
                                                fontSize = if (isActive) 16.sp else 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 20.sp
                                            )
                                            if (trans.note != null && trans.note.isNotBlank() && isActive) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = Color(0xFFFBBF24).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "💡 ${trans.note}",
                                                        color = Color(0xFFFBBF24),
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ──────────────────────────────────────────
        // SCRUBBER & TIME ELAPSED
        // ──────────────────────────────────────────
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(positionMs), fontSize = 12.sp, color = Color.Gray)
            Text("-${formatMs((durationMs - positionMs).coerceAtLeast(0L))}", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ──────────────────────────────────────────
        // PLAYBACK CONTROLS (APPLE MUSIC STYLE)
        // ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) Color(0xFF38BDF8) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Skip Previous
            IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }

            // Play / Pause Giant Button
            Surface(
                color = Color(0xFF38BDF8),
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .shadow(8.dp, CircleShape)
                    .clickable { viewModel.togglePlayPause() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // Skip Next
            IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }

            // Repeat
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // Dialog Pencarian Lirik Manual Online
    if (showManualSearchDialog) {
        var queryText by remember { mutableStateOf("${track!!.artist} ${track!!.title}") }
        Dialog(onDismissRequest = { showManualSearchDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Cari Lirik di LRCLIB", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ketik judul lagu dan nama artis:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showManualSearchDialog = false }) {
                            Text("Batal", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (queryText.isNotBlank()) {
                                    viewModel.searchManualLyrics(queryText)
                                    showManualSearchDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Cari", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// EMPTY LIBRARY PLACEHOLDER
// ─────────────────────────────────────────────────────────────
@Composable
fun EmptyLibraryPlaceholder(
    onPickFolder: () -> Unit,
    onRescan: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(60.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Tidak Ada Lagu Terdeteksi", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Penyimpanan perangkat belum terindeks atau file audio Anda berada di folder khusus. Pilih folder musik Anda secara langsung.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pilih Folder Musik (SAF)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai Ulang")
                }
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
        Text("AI Translation Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
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
