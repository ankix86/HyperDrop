package com.lantransfer.app.ui

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lantransfer.app.R

private data class UiTab(
    val title: String,
    @DrawableRes val iconRes: Int,
)

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val events by vm.events.collectAsState()
    val discoveredPeers by vm.discoveredPeers.collectAsState()

    var pairingCode by remember(settings.pairingCode) { mutableStateOf(settings.pairingCode) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val latestEvent = events.lastOrNull() ?: "Receiver ready"
    val tabs = remember {
        listOf(
            UiTab("Control", R.drawable.ic_tab_control),
            UiTab("Send", R.drawable.ic_tab_send),
            UiTab("Activity", R.drawable.ic_tab_activity),
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.sendUris(uris)
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.sendUris(uris)
    }
    val receiveTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setReceiveTree(uri)
        }
    }
    val folderSendPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            vm.sendFolderTree(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVfxBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        containerColor = Color(0xFF0A1137).copy(alpha = 0.96f),
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Image(
                                        painter = painterResource(tab.iconRes),
                                        contentDescription = tab.title,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .alpha(if (selectedTab == index) 1f else 0.7f)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF8DF6FF),
                                    selectedTextColor = Color(0xFFEAF2FF),
                                    unselectedIconColor = Color(0xFF8B95CC),
                                    unselectedTextColor = Color(0xFF8B95CC),
                                    indicatorColor = Color(0x3347DFFF)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 14.dp)
                ) {
                    HyperDropHero(settings.deviceId, latestEvent)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedTab) {
                            0 -> ControlTab(
                                pairingCode = pairingCode,
                                onPairingCodeChange = { pairingCode = it },
                                onSaveCode = { vm.updatePairingCode(pairingCode.trim()) },
                                onScanLan = vm::refreshDiscovery,
                                onUsePeer = vm::connectToPeer,
                                onStartService = vm::startService,
                                onStopService = vm::stopService,
                                settingsHost = settings.pcHost,
                                settingsPort = settings.pcPort,
                                discoveredPeers = discoveredPeers
                            )

                            1 -> SendTab(
                                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                                onPickMedia = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                onPickFolder = { folderSendPicker.launch(null) },
                                onPickReceivePath = { receiveTreePicker.launch(null) },
                                destination = settings.receiveTreeUri ?: "App storage"
                            )

                            else -> ActivityTab(events)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedVfxBackground(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "bgTransition")
    val glowShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowShift"
    )

    val centerX = 0.2f + (0.55f * glowShift)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .background(MaterialTheme.colorScheme.background)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0x663F3DFF),
                            Color(0x665C2CF6),
                            Color(0x0006071C)
                        ),
                        center = androidx.compose.ui.geometry.Offset(centerX, 0.2f),
                        radius = 1.1f
                    )
                )
        ) {
            BackgroundBubbles(glowShift)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            content()
        }
    }
}

@Composable
private fun BackgroundBubbles(phase: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val c1 = lerp(Color(0xFF53EEFF), Color(0xFFBE4BFF), phase)
        val c2 = lerp(Color(0xFF7A87FF), Color(0xFF53EEFF), 1f - phase)

        drawCircle(
            color = c1.copy(alpha = 0.09f),
            radius = w * 0.18f,
            center = androidx.compose.ui.geometry.Offset(w * (0.08f + 0.06f * phase), h * 0.18f)
        )
        drawCircle(
            color = c2.copy(alpha = 0.07f),
            radius = w * 0.14f,
            center = androidx.compose.ui.geometry.Offset(w * (0.86f - 0.05f * phase), h * 0.27f)
        )
        drawCircle(
            color = Color(0xFF53EEFF).copy(alpha = 0.06f),
            radius = w * 0.21f,
            center = androidx.compose.ui.geometry.Offset(w * 0.74f, h * (0.85f - 0.05f * phase))
        )
    }
}

@Composable
private fun HyperDropHero(deviceId: String, latestEvent: String) {
    val pulse by rememberInfiniteTransition(label = "heroPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "pulse"
    )
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0D123A),
                            Color(0xFF2A1D73),
                            Color(0xFF0A5ACB)
                        )
                    )
                )
        ) {
            HeroBubbles(pulse)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("HyperDrop", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Lightning-fast encrypted local transfer", color = Color(0xFFE6EEFF))
                Text(
                    text = "Device: $deviceId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB9C8FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Latest: $latestEvent",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD6DFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroBubbles(pulse: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = 0.65f + (pulse * 0.35f)

        drawCircle(
            color = Color(0xFF53EEFF).copy(alpha = 0.22f * p),
            radius = h * 0.42f,
            center = androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.02f)
        )
        drawCircle(
            color = Color(0xFFB45BFF).copy(alpha = 0.16f * p),
            radius = h * 0.34f,
            center = androidx.compose.ui.geometry.Offset(w * 0.73f, h * 0.15f)
        )
        drawCircle(
            color = Color(0xFF9CC7FF).copy(alpha = 0.12f * p),
            radius = h * 0.22f,
            center = androidx.compose.ui.geometry.Offset(w * 0.92f, h * 0.56f)
        )
    }
}

@Composable
private fun ControlTab(
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onSaveCode: () -> Unit,
    onScanLan: () -> Unit,
    onUsePeer: (com.lantransfer.app.network.DiscoveredPeer) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    settingsHost: String,
    settingsPort: Int,
    discoveredPeers: List<com.lantransfer.app.network.DiscoveredPeer>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (settingsHost.isBlank()) "Target: not selected" else "Target: $settingsHost:$settingsPort",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = onPairingCodeChange,
                        label = { Text("Pairing Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onSaveCode, modifier = Modifier.weight(1f)) { Text("Save") }
                        FilledTonalButton(onClick = onScanLan, modifier = Modifier.weight(1f)) { Text("Scan LAN") }
                    }
                    if (discoveredPeers.isEmpty()) {
                        Text("No devices found yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(discoveredPeers) { peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(peer.deviceName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${peer.platform} - ${peer.ip}:${peer.port}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(onClick = { onUsePeer(peer) }) { Text("Use") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Receiver Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Keep this on for PC -> Android transfers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(onClick = onStartService, modifier = Modifier.weight(1f)) { Text("Start") }
                        FilledTonalButton(onClick = onStopService, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                    HorizontalDivider()
                    Text("Tip: screenshot share from any app can be sent directly to HyperDrop.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SendTab(
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFolder: () -> Unit,
    onPickReceivePath: () -> Unit,
    destination: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) { Text("Files") }
                    FilledTonalButton(onClick = onPickMedia, modifier = Modifier.weight(1f)) { Text("Media") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) { Text("Folder") }
                    FilledTonalButton(onClick = onPickReceivePath, modifier = Modifier.weight(1f)) { Text("Receive Path") }
                }
                Text(
                    text = "Destination: $destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActivityTab(events: List<String>) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Transfer Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (events.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No transfer events yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(events.takeLast(150)) { line ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}