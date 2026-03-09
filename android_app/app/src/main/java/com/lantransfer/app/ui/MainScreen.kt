package com.lantransfer.app.ui

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lantransfer.app.R
import kotlin.math.PI
import kotlin.math.sin

private data class UiTab(val title: String, @DrawableRes val iconRes: Int)
private enum class BackgroundGlyphKind { Triangle, Diamond, Circle, Cross }
private data class BackgroundGlyphSpec(
    val x: Float,
    val y: Float,
    val kind: BackgroundGlyphKind,
    val size: Float,
    val drift: Float,
    val spin: Float,
)

private val backgroundGlyphs = listOf(
    BackgroundGlyphSpec(0.08f, 0.18f, BackgroundGlyphKind.Triangle, 44f, 18f, 7f),
    BackgroundGlyphSpec(0.16f, 0.74f, BackgroundGlyphKind.Diamond, 42f, 16f, -6f),
    BackgroundGlyphSpec(0.30f, 0.28f, BackgroundGlyphKind.Circle, 34f, 12f, 5f),
    BackgroundGlyphSpec(0.42f, 0.62f, BackgroundGlyphKind.Cross, 40f, 14f, -7f),
    BackgroundGlyphSpec(0.58f, 0.18f, BackgroundGlyphKind.Triangle, 40f, 18f, 8f),
    BackgroundGlyphSpec(0.70f, 0.76f, BackgroundGlyphKind.Diamond, 38f, 15f, 6f),
    BackgroundGlyphSpec(0.82f, 0.36f, BackgroundGlyphKind.Circle, 34f, 14f, -5f),
    BackgroundGlyphSpec(0.92f, 0.64f, BackgroundGlyphKind.Triangle, 42f, 17f, 9f),
)

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val discoveredPeers by vm.discoveredPeers.collectAsState()
    val sendSelection by vm.sendSelection.collectAsState()
    val senderRequest by vm.senderRequest.collectAsState()
    val receiveSession by vm.receiveSession.collectAsState()
    val isServiceRunning by vm.isServiceRunning.collectAsState()
    val incomingTransfer by vm.incomingTransfer.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val receiveStatusTabIndex = 3
    val tabs = remember {
        listOf(
            UiTab("Receive", R.drawable.ic_tab_activity),
            UiTab("Send", R.drawable.ic_tab_send),
            UiTab("Settings", R.drawable.ic_tab_control),
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addUrisToSelection(uris)
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.addUrisToSelection(uris)
    }
    val receiveTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setReceiveTree(uri)
        }
    }
    val incomingTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setIncomingTransferFolder(uri)
        }
    }
    LaunchedEffect(incomingTransfer?.transferId) {
        if (incomingTransfer != null) selectedTab = 0
    }
    LaunchedEffect(receiveSession?.transferId, receiveSession != null) {
        if (receiveSession != null) {
            selectedTab = receiveStatusTabIndex
        } else if (selectedTab == receiveStatusTabIndex) {
            selectedTab = 0
        }
    }
    LaunchedEffect(sendSelection.size) {
        if (sendSelection.isNotEmpty() && incomingTransfer == null && receiveSession == null) {
            selectedTab = 1
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)),
                        containerColor = Color(0xFF0A1137).copy(alpha = 0.96f),
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val tabSelected = selectedTab == index || (selectedTab == receiveStatusTabIndex && index == 0)
                            NavigationBarItem(
                                selected = tabSelected,
                                onClick = {
                                    if (receiveSession != null) return@NavigationBarItem
                                    selectedTab = index
                                },
                                icon = {
                                    Box {
                                        Image(
                                            painter = painterResource(tab.iconRes),
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(22.dp).alpha(if (tabSelected) 1f else 0.7f)
                                        )
                                        if (tab.title == "Receive" && (incomingTransfer != null || receiveSession != null)) {
                                            Box(
                                                Modifier.size(8.dp)
                                                    .background(Color(0xFFFF6B9D), CircleShape)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-4).dp)
                                            )
                                        }
                                    }
                                },
                                label = { Text(tab.title, fontWeight = if (tabSelected) FontWeight.SemiBold else FontWeight.Medium) },
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
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 14.dp)
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (selectedTab) {
                            0 -> ReceiveTab(
                                isServiceRunning = isServiceRunning,
                                deviceName = settings.deviceName,
                                receiveLocation = if (settings.receiveTreeUri.isNullOrBlank()) "App storage" else "Custom folder selected",
                                hasIncomingRequest = incomingTransfer != null,
                                onApplyDeviceName = vm::updateDeviceName,
                                onStartService = vm::startService,
                                onStopService = vm::stopService,
                                onPickReceivePath = { receiveTreePicker.launch(null) },
                            )
                            1 -> SendTab(
                                discoveredPeers = discoveredPeers,
                                selection = sendSelection,
                                onSendToPeer = vm::sendSelectionToPeer,
                                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                                onPickMedia = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                onRemoveSelected = vm::removeSendSelectionItem,
                                onClearSelection = vm::clearSendSelection,
                            )
                            receiveStatusTabIndex -> {
                                val session = receiveSession
                                if (session != null) {
                                    ReceiveStatusTab(
                                        session = session,
                                        onOpenReceivedFile = vm::openReceivedSessionFile,
                                        onDone = vm::completeReceiveSession,
                                    )
                                } else {
                                    ReceiveTab(
                                        isServiceRunning = isServiceRunning,
                                        deviceName = settings.deviceName,
                                        receiveLocation = if (settings.receiveTreeUri.isNullOrBlank()) "App storage" else "Custom folder selected",
                                        hasIncomingRequest = incomingTransfer != null,
                                        onApplyDeviceName = vm::updateDeviceName,
                                        onStartService = vm::startService,
                                        onStopService = vm::stopService,
                                        onPickReceivePath = { receiveTreePicker.launch(null) },
                                    )
                                }
                            }
                            else -> SettingsTab(
                                receiveLocation = if (settings.receiveTreeUri.isNullOrBlank()) "App storage" else "Custom folder selected",
                                onPickReceivePath = { receiveTreePicker.launch(null) },
                                onOpenInfo = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/ankix86/HyperDrop")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        incomingTransfer?.let { state ->
            IncomingTransferDialog(
                state = state,
                onPickFolder = { incomingTreePicker.launch(null) },
                onUseAppStorage = vm::useAppStorageForIncomingTransfer,
                onToggleSelected = vm::setIncomingTransferItemSelected,
                onRename = vm::renameIncomingTransferItem,
                onAccept = vm::acceptIncomingTransfer,
                onDecline = vm::declineIncomingTransfer,
                onAcknowledgeClosed = vm::acknowledgeIncomingTransferSenderClosed,
            )
        }

        senderRequest?.let { state ->
            SenderRequestDialog(
                state = state,
                onAction = vm::onSenderRequestAction,
            )
        }
    }
}

@Composable
private fun AnimatedBackground(content: @Composable () -> Unit) {
    val phase by rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phase"
    )
    val phaseB by rememberInfiniteTransition(label = "bgB").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phaseB"
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B1D), Color(0xFF0A1140), Color(0xFF09112B))
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x4D7A54FF), Color(0x1A7A54FF), Color(0x0006071C)),
                    center = androidx.compose.ui.geometry.Offset(w * (0.2f + 0.4f * phase), h * 0.2f),
                    radius = w
                )
            )
            drawCircle(
                Color(0x187A54FF),
                w * 0.22f,
                androidx.compose.ui.geometry.Offset(w * (0.08f + 0.04f * phase), h * (0.74f - 0.05f * phaseB))
            )
            drawCircle(
                Color(0x145EB4FF),
                w * 0.18f,
                androidx.compose.ui.geometry.Offset(w * (0.92f - 0.05f * phaseB), h * (0.14f + 0.06f * phase))
            )

            backgroundGlyphs.forEachIndexed { index, spec ->
                val center = Offset(
                    x = w * spec.x + (sin((phase + index * 0.11f) * PI * 2).toFloat() * spec.drift),
                    y = h * spec.y + (sin((phaseB + index * 0.09f) * PI * 2).toFloat() * spec.drift * 0.68f),
                )
                val rotation = sin((phase + index * 0.07f) * PI * 2).toFloat() * spec.spin
                drawBackgroundGlyph(
                    kind = spec.kind,
                    center = center,
                    sizePx = spec.size,
                    rotation = rotation,
                    tint = when (spec.kind) {
                        BackgroundGlyphKind.Triangle -> Color(0x9979AAFF)
                        BackgroundGlyphKind.Diamond -> Color(0x997BAEFF)
                        BackgroundGlyphKind.Circle -> Color(0x8876A8FF)
                        BackgroundGlyphKind.Cross -> Color(0x888D9DFF)
                    }
                )
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x18050B24), Color.Transparent, Color(0x1D050B24))
                )
            )
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackgroundGlyph(
    kind: BackgroundGlyphKind,
    center: Offset,
    sizePx: Float,
    rotation: Float,
    tint: Color,
) {
    withTransform({
        rotate(rotation, pivot = center)
    }) {
        when (kind) {
            BackgroundGlyphKind.Triangle -> {
                val path = Path().apply {
                    moveTo(center.x, center.y - sizePx * 0.48f)
                    lineTo(center.x + sizePx * 0.46f, center.y + sizePx * 0.42f)
                    lineTo(center.x - sizePx * 0.46f, center.y + sizePx * 0.42f)
                    close()
                }
                drawPath(path, color = tint, style = Stroke(width = sizePx * 0.06f))
            }

            BackgroundGlyphKind.Diamond -> {
                val path = Path().apply {
                    moveTo(center.x, center.y - sizePx * 0.42f)
                    lineTo(center.x + sizePx * 0.42f, center.y)
                    lineTo(center.x, center.y + sizePx * 0.42f)
                    lineTo(center.x - sizePx * 0.42f, center.y)
                    close()
                }
                drawPath(path, color = tint, style = Stroke(width = sizePx * 0.06f))
            }

            BackgroundGlyphKind.Circle -> {
                drawCircle(
                    color = tint,
                    radius = sizePx * 0.34f,
                    center = center,
                    style = Stroke(width = sizePx * 0.06f)
                )
            }

            BackgroundGlyphKind.Cross -> {
                drawLine(
                    color = tint,
                    start = Offset(center.x, center.y - sizePx * 0.34f),
                    end = Offset(center.x, center.y + sizePx * 0.34f),
                    strokeWidth = sizePx * 0.06f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(center.x - sizePx * 0.34f, center.y),
                    end = Offset(center.x + sizePx * 0.34f, center.y),
                    strokeWidth = sizePx * 0.06f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ReceiveTab(
    isServiceRunning: Boolean,
    deviceName: String,
    receiveLocation: String,
    hasIncomingRequest: Boolean,
    onApplyDeviceName: (String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onPickReceivePath: () -> Unit,
) {
    var editingName by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable(deviceName) { mutableStateOf(deviceName) }
    LaunchedEffect(deviceName, editingName) {
        if (!editingName) {
            draftName = deviceName
        }
    }
    val pulse by rememberInfiniteTransition(label = "receiverPulse").animateFloat(
        initialValue = 0.97f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
    ) {
        if (hasIncomingRequest) item { NoticeCard("Incoming request", "Approval is waiting in this tab.") }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .border(4.dp, Color(0xFF58E7FF), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF58E7FF))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!editingName) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    draftName = deviceName
                                    editingName = true
                                }
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_edit_line),
                                    contentDescription = "Edit device name",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Edit")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it.take(42) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Device name") }
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        draftName = deviceName
                                        editingName = false
                                    }
                                ) {
                                    Text("Cancel")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onApplyDeviceName(draftName)
                                        editingName = false
                                    },
                                    enabled = draftName.trim().isNotEmpty() && draftName.trim() != deviceName
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ShellCard(modifier = Modifier.fillMaxWidth(0.9f)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { if (it) onStartService() else onStopService() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF1E8A3B))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendTab(
    discoveredPeers: List<com.lantransfer.app.network.DiscoveredPeer>,
    selection: List<MainViewModel.SendSelectionItemUi>,
    onSendToPeer: (com.lantransfer.app.network.DiscoveredPeer) -> Unit,
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onRemoveSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val totalSize = selection.sumOf { it.sizeBytes }
    val hasPeers = discoveredPeers.isNotEmpty()
    var selectionManagerOpen by rememberSaveable { mutableStateOf(false) }
    val previewLimit = 6
    val previewItems = remember(selection) { selection.take(previewLimit) }
    val overflowCount = (selection.size - previewItems.size).coerceAtLeast(0)
    val spinnerRotation by rememberInfiniteTransition(label = "nearbySpinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRotation"
    )

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ShellCard {
                Text("Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Files: ${selection.size}", color = Color(0xFFEAF2FF))
                Text("Size: ${formatBytes(totalSize)}", color = Color(0xFF8BA4D4))
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101A43)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selection.isEmpty()) {
                            SelectionItemGlyph(compact = false, tint = Color(0xFF72DFFF))
                            Text(
                                "No items selected yet",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Use Add or Media to build your collection, then open Edit to manage every item.",
                                color = Color(0xFF8BA4D4),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                previewItems.forEach { item ->
                                    SelectionPreviewTile(
                                        label = item.fileName,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                                if (overflowCount > 0) {
                                    SelectionPreviewTile(
                                        overflowCount = overflowCount,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                            Text(
                                "Previewing your current collection. Tap Edit to review names, remove items, or clear everything.",
                                color = Color(0xFF8BA4D4),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) { Text("+ Add") }
                    FilledTonalButton(onClick = onPickMedia, modifier = Modifier.weight(1f)) { Text("Media") }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { selectionManagerOpen = true },
                        enabled = selection.isNotEmpty()
                    ) { Text("Edit") }
                }
            }
        }
        item {
            ShellCard {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Nearby devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(if (discoveredPeers.isEmpty()) "Looking for devices on your network" else "Found ${discoveredPeers.size} device(s)", color = Color(0xFF8BA4D4))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasPeers) "${discoveredPeers.size} online" else "Searching",
                            color = if (hasPeers) Color(0xFF6EE6FF) else Color(0xFF8BA4D4),
                            fontWeight = FontWeight.SemiBold
                        )
                        NearbyStatusGlyph(
                            online = hasPeers,
                            rotation = spinnerRotation,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (discoveredPeers.isEmpty()) {
                    Text("No devices found yet", color = Color(0xFF8BA4D4))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredPeers.forEach { peer ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33223B93))
                                    .border(1.dp, Color(0x332C7CFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = peer.deviceName,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFEAF2FF),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { onSendToPeer(peer) },
                                    enabled = selection.isNotEmpty()
                                ) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectionManagerOpen) {
        SelectionManagerDialog(
            selection = selection,
            onDismiss = { selectionManagerOpen = false },
            onPickFiles = onPickFiles,
            onPickMedia = onPickMedia,
            onRemoveSelected = onRemoveSelected,
            onClearSelection = onClearSelection,
        )
    }
}

@Composable
private fun SelectionManagerDialog(
    selection: List<MainViewModel.SendSelectionItemUi>,
    onDismiss: () -> Unit,
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onRemoveSelected: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09112A)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Back") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Selection", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${selection.size} file(s) - ${formatBytes(selection.sumOf { it.sizeBytes })}",
                            color = Color(0xFF8BA4D4),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = onClearSelection, enabled = selection.isNotEmpty()) { Text("Clear All") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) { Text("+ Add") }
                    FilledTonalButton(onClick = onPickMedia, modifier = Modifier.weight(1f)) { Text("Media") }
                }

                if (selection.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SelectionItemGlyph(compact = false, tint = Color(0xFF72DFFF))
                            Spacer(Modifier.height(10.dp))
                            Text("Your selection is empty", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add files or media to rebuild the collection.",
                                color = Color(0xFF8BA4D4),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selection, key = { it.uriString }) { item ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111B44)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SelectionItemGlyph(compact = true, tint = Color(0xFF72DFFF))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.fileName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatBytes(item.sizeBytes), color = Color(0xFF8BA4D4), style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { onRemoveSelected(item.uriString) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionPreviewTile(
    label: String? = null,
    overflowCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (overflowCount > 0) Color(0xFF1A2A57) else Color(0xFF13224B)
        ),
        modifier = modifier.size(58.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (overflowCount > 0) {
                Text("+$overflowCount", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                SelectionItemGlyph(compact = true, tint = Color(0xFF72DFFF))
            }
        }
    }
}

@Composable
private fun SelectionItemGlyph(
    compact: Boolean,
    tint: Color,
) {
    val cardSize = if (compact) 22.dp else 30.dp
    val foldSize = if (compact) 7.dp else 9.dp
    Box(
        modifier = Modifier.size(if (compact) 28.dp else 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                .background(Color(0xFF172750))
                .border(1.dp, tint, RoundedCornerShape(if (compact) 6.dp else 8.dp))
        )
        Box(
            modifier = Modifier
                .size(foldSize)
                .offset(x = if (compact) 7.dp else 10.dp, y = if (compact) (-7).dp else (-10).dp)
                .background(Color(0xFF0F1D42))
                .border(1.dp, tint, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun NearbyStatusGlyph(
    online: Boolean,
    rotation: Float,
) {
    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.sarching_icon),
            contentDescription = if (online) "Online devices" else "Searching",
            modifier = Modifier
                .size(17.dp)
                .rotate(rotation)
                .alpha(if (online) 1f else 0.72f)
        )
    }
}

@Composable
private fun ReceiveStatusTab(
    session: MainViewModel.ReceiveSessionUiState,
    onOpenReceivedFile: (String?) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            session.statusTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Save to folder: ${session.targetLabel}",
            color = Color(0xFF9AB1EA),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1335)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (session.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No files in this session yet.", color = Color(0xFFC8D8FF))
                        Spacer(Modifier.height(4.dp))
                        Text("Files will appear here when transfer starts.", color = Color(0xFF8FA3DC), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(session.files, key = { it.relativePath }) { file ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2447)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${file.fileName} (${formatBytes(file.sizeBytes)})",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusText = if (file.done) "Done" else "${formatBytes(file.receivedBytes)} / ${formatBytes(file.sizeBytes)}"
                                    Text(statusText, color = if (file.done) Color(0xFF7FD1FF) else Color(0xFFBFCFF5), style = MaterialTheme.typography.bodySmall)
                                }
                                if (file.done && !file.openUri.isNullOrBlank()) {
                                    FilledTonalButton(onClick = { onOpenReceivedFile(file.openUri) }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    session.statusTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                LinearProgressIndicator(
                    progress = { session.ratio },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                    color = Color(0xFFA7CCFF),
                    trackColor = Color(0xFF354462),
                    strokeCap = StrokeCap.Round
                )
                Text("Files: ${session.completedFiles}/${session.totalFiles}", color = Color(0xFFD9E6FF), style = MaterialTheme.typography.bodyMedium)
                Text("Size: ${formatBytes(session.receivedBytes)} / ${formatBytes(session.totalBytes)}", color = Color(0xFFD9E6FF), style = MaterialTheme.typography.bodyMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDone,
                        enabled = !session.active,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF44D671),
                            contentColor = Color(0xFF08122E),
                            disabledContainerColor = Color(0xFF22603A),
                            disabledContentColor = Color(0xFF9EC6AE)
                        )
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    receiveLocation: String,
    onPickReceivePath: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                FilledTonalButton(onClick = onOpenInfo) { Text("Info") }
            }
        }
        item {
            ShellCard {
                Text("Receiving Folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(receiveLocation, color = Color(0xFFEAF2FF))
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onPickReceivePath) { Text("Change default folder") }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.hyperdrop_wordmark),
                    contentDescription = "HyperDrop",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Version: 1.0.0",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFEAF2FF)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u00A9 2026 RayZDev",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB6C5F8)
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, text: String) {
    ShellCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color(0xFF8DF6FF))
    }
}

@Composable
private fun ShellCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun IncomingTransferDialog(
    state: MainViewModel.IncomingTransferUiState,
    onPickFolder: () -> Unit,
    onUseAppStorage: () -> Unit,
    onToggleSelected: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onAcknowledgeClosed: () -> Unit,
) {
    Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09112A)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Incoming transfer", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (state.senderClosed) "Sender closed the session before you responded."
                    else "${state.senderName} wants to send files to this device.",
                    color = Color(0xFFEAF2FF)
                )
                if (!state.senderClosed) {
                    ShellCard {
                        Text("Save to", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(state.receiveTargetLabel, color = Color(0xFFEAF2FF))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) { Text("Choose folder") }
                            TextButton(onClick = onUseAppStorage, modifier = Modifier.weight(1f)) { Text("Use app storage") }
                        }
                    }
                }
                Text("${state.items.count { !it.isDirectory }} file(s) incoming", style = MaterialTheme.typography.titleSmall, color = Color(0xFF8DF6FF), fontWeight = FontWeight.SemiBold)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items, key = { it.relativePath }) { item ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111B44)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (!item.isDirectory && !state.senderClosed) {
                                        Checkbox(checked = item.selected, onCheckedChange = { onToggleSelected(item.relativePath, it) })
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.fileName, color = if (item.selected || item.isDirectory) Color.White else Color(0xFF7C87B8), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (item.isDirectory) item.relativePath else "${item.relativePath} • ${formatBytes(item.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8BA4D4),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (item.isDirectory) Text("Folder", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8DF6FF))
                                }
                                if (!state.senderClosed && !item.isDirectory && item.selected) {
                                    OutlinedTextField(value = item.proposedName, onValueChange = { onRename(item.relativePath, it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Save as") })
                                }
                            }
                        }
                    }
                }
                if (state.senderClosed) {
                    Button(
                        onClick = onAcknowledgeClosed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB6FF), contentColor = Color(0xFF0C1A3B))
                    ) { Text("Okay") }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDecline,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F2948), contentColor = Color.White)
                        ) { Text("Decline") }
                        Button(
                            onClick = onAccept,
                            enabled = state.canAccept,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FB6FF), contentColor = Color(0xFF0C1A3B))
                        ) { Text("Accept") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferProgressCard(progress: MainViewModel.TransferProgress) {
    val animatedProgress by animateFloatAsState(progress.ratio, tween(220), label = "transferProgress")
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active transfer", style = MaterialTheme.typography.titleSmall, color = Color(0xFF8DF6FF), fontWeight = FontWeight.Bold)
                    Text(progress.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFEAF2FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF103148))
                        .border(1.dp, Color(0xFF1E7FA2), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("${(progress.ratio * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF58E7FF))
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                color = Color(0xFF58E7FF),
                trackColor = Color(0xFF14245B),
                strokeCap = StrokeCap.Round
            )
            Text("${formatBytes(progress.sent)} / ${formatBytes(progress.total)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8BA4D4))
            Button(
                onClick = { MainViewModel.cancelTransfer() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7D3158), contentColor = Color.White)
            ) { Text("Cancel Transfer") }
        }
    }
}

@Composable
private fun SenderRequestDialog(
    state: MainViewModel.SenderRequestUiState,
    onAction: () -> Unit,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09112A)),
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SenderRequestDeviceCard(
                    title = state.senderName,
                    platform = "Android",
                )
                Text("v", color = Color(0xFFEAF2FF), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                SenderRequestDeviceCard(
                    title = state.receiverName,
                    platform = "Receiver",
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    state.message,
                    color = Color(0xFFEAF2FF),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.waiting) Color(0xFF8FB6FF) else Color(0xFF5A6AA6),
                        contentColor = Color(0xFF0C1A3B)
                    )
                ) {
                    Text(state.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SenderRequestDeviceCard(
    title: String,
    platform: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B4762)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF8FB6FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(platform.take(1), color = Color(0xFF08122E), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(platform, color = Color(0xFFD4E0FF), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
