package com.lantransfer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lantransfer.app.network.DiscoveredPeer
import com.lantransfer.app.network.LanDiscovery
import com.lantransfer.app.service.IncomingTransferDecision
import com.lantransfer.app.service.IncomingTransferItem
import com.lantransfer.app.service.IncomingTransferRequest
import com.lantransfer.app.service.TransferForegroundService
import com.lantransfer.app.service.TransferServiceBus
import com.lantransfer.app.settings.AppSettings
import com.lantransfer.app.settings.AppSettingsRepository
import com.lantransfer.app.storage.StorageRepository
import com.lantransfer.app.transfer.OutgoingTransferQueue
import kotlinx.coroutines.Dispatchers
import com.lantransfer.app.transfer.TransferEngine
import com.lantransfer.app.util.PathSanitizer
import com.lantransfer.app.util.SfxPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        fun cancelTransfer() {
            TransferServiceBus.emit("cancel")
        }
    }

    data class IncomingTransferUiItem(
        val relativePath: String,
        val fileName: String,
        val parentPath: String,
        val sizeBytes: Long,
        val isDirectory: Boolean,
        val selected: Boolean,
        val proposedName: String,
    )

    data class IncomingTransferUiState(
        val transferId: String,
        val senderName: String,
        val receiveTreeUri: String?,
        val receiveTargetLabel: String,
        val items: List<IncomingTransferUiItem>,
        val senderClosed: Boolean = false,
    ) {
        val canAccept: Boolean
            get() = !senderClosed && items.any { !it.isDirectory && it.selected }
    }

    private val settingsRepo = AppSettingsRepository(app)
    private val transferEngine = TransferEngine(app)
    private val storageRepo = StorageRepository(app)
    private val sfxPlayer = SfxPlayer(app)
    private val discovery = LanDiscovery(
        deviceIdProvider = { _settings.value.deviceId },
        deviceNameProvider = { _settings.value.deviceName },
        tcpPortProvider = { _settings.value.localPort },
        platform = "android"
    )
    // Removed redundant discoveryResponder. Managed solely by TransferForegroundService now.

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    data class TransferProgress(val fileName: String, val sent: Long, val total: Long) {
        val ratio: Float get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data class SendSelectionItemUi(
        val uriString: String,
        val fileName: String,
        val sizeBytes: Long,
    )

    data class SenderRequestUiState(
        val senderName: String,
        val senderId: String,
        val receiverName: String,
        val receiverId: String,
        val waiting: Boolean,
    ) {
        val message: String
            get() = if (waiting) "Waiting for Response.." else "Receiver Reject the Request"
        val actionLabel: String
            get() = if (waiting) "Cancel" else "Close"
    }

    data class ReceiveSessionFileUi(
        val relativePath: String,
        val fileName: String,
        val sizeBytes: Long,
        val receivedBytes: Long,
        val done: Boolean,
        val openUri: String?,
    )

    data class ReceiveSessionUiState(
        val transferId: String,
        val targetLabel: String,
        val files: List<ReceiveSessionFileUi>,
        val active: Boolean,
        val finished: Boolean,
        val cancelled: Boolean,
    ) {
        val totalFiles: Int get() = files.size
        val completedFiles: Int get() = files.count { it.done }
        val totalBytes: Long get() = files.sumOf { it.sizeBytes }
        val receivedBytes: Long get() = files.sumOf { file ->
            val cap = if (file.sizeBytes > 0L) file.sizeBytes else file.receivedBytes
            file.receivedBytes.coerceAtMost(cap)
        }
        val ratio: Float
            get() = if (totalBytes > 0L) (receivedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        val statusTitle: String
            get() = when {
                active -> "Receiving files"
                finished -> "Finished"
                cancelled -> "Cancelled"
                else -> "Receive session"
            }
    }

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()
    private val _sendSelection = MutableStateFlow<List<SendSelectionItemUi>>(emptyList())
    val sendSelection: StateFlow<List<SendSelectionItemUi>> = _sendSelection.asStateFlow()
    private var pendingSenderSelectionRestore: List<SendSelectionItemUi> = emptyList()
    private var lastHandledShareEventId: Long = 0L
    private val _senderRequest = MutableStateFlow<SenderRequestUiState?>(null)
    val senderRequest: StateFlow<SenderRequestUiState?> = _senderRequest.asStateFlow()
    private val _receiveSession = MutableStateFlow<ReceiveSessionUiState?>(null)
    val receiveSession: StateFlow<ReceiveSessionUiState?> = _receiveSession.asStateFlow()

    private val _incomingTransfer = MutableStateFlow<IncomingTransferUiState?>(null)
    val incomingTransfer: StateFlow<IncomingTransferUiState?> = _incomingTransfer.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    val isServiceRunning = TransferServiceBus.isServiceRunning

    private val _settings = MutableStateFlow(
        AppSettings("", "", "", 54545, 54545, false, null, "system")
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val queue = OutgoingTransferQueue(
        scope = viewModelScope,
        worker = { task ->
            when (task) {
                is OutgoingTransferQueue.Task.UriBatch -> transferEngine.sendUrisToPc(task.settings, task.uris) { push(it) }
                is OutgoingTransferQueue.Task.FolderTree -> transferEngine.sendTreeToPc(task.settings, task.treeUri) { push(it) }
            }
        },
        onEvent = { push(it) }
    )

    init {
        viewModelScope.launch {
            val s = settingsRepo.settingsFlow.first()
            settingsRepo.persistGeneratedDeviceIdIfNeeded(s)
        }
        viewModelScope.launch {
            settingsRepo.settingsFlow.collectLatest { _settings.value = it }
        }
        // Keep Android receiver available for PC -> Android sends without manual start.
        startService()
        viewModelScope.launch {
            TransferServiceBus.events.collectLatest { event ->
                when {
                    event == "cancel" -> {
                        val shouldRestoreSelection = _senderRequest.value?.waiting == true && pendingSenderSelectionRestore.isNotEmpty()
                        transferEngine.cancelTransfer()
                        queue.clear()
                        _transferProgress.value = null
                        _senderRequest.value = null
                        if (shouldRestoreSelection) {
                            restorePendingSenderSelection()
                        }
                        push("Transfer cancelled by user")
                    }
                    event.startsWith("peer_offline:") -> {
                        val departedId = event.substringAfter("peer_offline:")
                        val s = _settings.value
                        val pairedPeer = _discoveredPeers.value.firstOrNull { it.deviceId == departedId }
                        if (pairedPeer != null && pairedPeer.ip == s.pcHost) {
                            settingsRepo.updateConnection("", s.pcPort)
                        }
                        _discoveredPeers.value = _discoveredPeers.value.filter { it.deviceId != departedId }
                    }
                    else -> push(event)
                }
            }
        }
        viewModelScope.launch {
            TransferServiceBus.incomingTransfer.collectLatest { request ->
                if (request != null) {
                    _incomingTransfer.value = request.toUiState()
                } else if (_incomingTransfer.value?.senderClosed != true) {
                    _incomingTransfer.value = null
                }
            }
        }
        viewModelScope.launch {
            IncomingShareBus.events.collectLatest { event ->
                if (event.id <= lastHandledShareEventId) return@collectLatest
                lastHandledShareEventId = event.id
                handleSharedUris(event.uris)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshDiscovery(silent = true)
                delay(4500)
            }
        }
    }

    private fun push(text: String) {
        if (text.startsWith("INCOMING_TRANSFER:")) {
            handleIncomingTransferSignal(text)
            return
        }
        if (text.startsWith("RECEIVE_SESSION:")) {
            handleReceiveSessionSignal(text)
            return
        }
        if (text.startsWith("SENDER_REQUEST:")) {
            handleSenderRequestSignal(text)
            return
        }
        if (text.startsWith("PROGRESS:")) {
            runCatching {
                val parts = text.split(":", limit = 4)
                val rel = parts[1]
                val sent = parts[2].toLong()
                val total = parts[3].toLong()
                val fileName = rel.substringAfterLast('/').substringAfterLast('\\')
                if (rel == "error" || rel == "cancelled") {
                    if (_receiveSession.value?.active == true) {
                        _receiveSession.value = _receiveSession.value?.copy(active = false, cancelled = rel == "cancelled")
                    }
                    if (pendingSenderSelectionRestore.isNotEmpty()) {
                        restorePendingSenderSelection()
                    }
                    if (_senderRequest.value?.waiting == true) {
                        _senderRequest.value = null
                    }
                    _transferProgress.value = null
                    return
                }
                updateReceiveSessionProgress(relativePath = rel, sent = sent, total = total)
                pendingSenderSelectionRestore = emptyList()
                _senderRequest.value = null
                _transferProgress.value = TransferProgress(fileName, sent, total)
                if (sent >= total && total > 0) {
                    viewModelScope.launch {
                        delay(1500)
                        _transferProgress.value = null
                    }
                }
            }
            return
        }
        _events.value = (_events.value + text).takeLast(200)
        sfxPlayer.playForEvent(text)
    }

    fun startService() {
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, TransferForegroundService::class.java).apply { action = TransferForegroundService.ACTION_START })
    }

    fun stopService() {
        viewModelScope.launch(Dispatchers.IO) { discovery.sendBye() }
        val app = getApplication<Application>()
        app.startService(Intent(app, TransferForegroundService::class.java).apply { action = TransferForegroundService.ACTION_STOP })
    }

    fun restartService() {
        val wasRunning = isServiceRunning.value
        if (!wasRunning) {
            startService()
            return
        }
        stopService()
        viewModelScope.launch {
            delay(350)
            startService()
        }
    }

    fun updateLocalPort(port: Int) {
        if (port !in 1024..65535) {
            push("Port must be between 1024 and 65535")
            return
        }
        viewModelScope.launch {
            val current = settingsRepo.settingsFlow.first()
            if (current.localPort == port) {
                push("Communication port is already $port")
                return@launch
            }
            settingsRepo.updateLocalPort(port)
            push(
                if (isServiceRunning.value) {
                    "Communication port saved as $port. Restart the server to apply it."
                } else {
                    "Communication port saved as $port"
                }
            )
        }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch { settingsRepo.updateDeviceName(name) }
    }

    fun connectToPeer(peer: DiscoveredPeer) {
        viewModelScope.launch {
            settingsRepo.updateConnection(peer.ip, peer.port)
            push("Connected target: ${peer.deviceName} (${peer.ip}:${peer.port})")
        }
    }

    fun setThemePreference(theme: String) {
        viewModelScope.launch {
            settingsRepo.setThemePreference(theme)
        }
    }

    fun refreshDiscovery(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) push("Scanning local network...")
            val peers = runCatching { discovery.discover() }.getOrElse {
                if (!silent) push("discovery failed: ${it.message}")
                emptyList()
            }
            _discoveredPeers.value = peers
            // If the paired target is no longer discoverable, auto-deselect it
            val currentHost = _settings.value.pcHost
            val currentPort = _settings.value.pcPort
            if (currentHost.isNotBlank() && peers.none { it.ip == currentHost && it.port == currentPort }) {
                settingsRepo.updateConnection("", currentPort)
            }
            if (!silent) push("Found ${peers.size} device(s)")
        }
    }

    fun setReceiveTree(uri: Uri?) {
        viewModelScope.launch { settingsRepo.setReceiveTree(uri?.toString()) }
    }

    fun setIncomingTransferFolder(uri: Uri?) {
        val uriString = uri?.toString()
        _incomingTransfer.value = _incomingTransfer.value?.copy(
            receiveTreeUri = uriString,
            receiveTargetLabel = storageRepo.describeReceiveTarget(uriString),
        )
    }

    fun useAppStorageForIncomingTransfer() {
        _incomingTransfer.value = _incomingTransfer.value?.copy(
            receiveTreeUri = null,
            receiveTargetLabel = storageRepo.describeReceiveTarget(null),
        )
    }

    fun setIncomingTransferItemSelected(relativePath: String, selected: Boolean) {
        _incomingTransfer.value = _incomingTransfer.value?.copy(
            items = _incomingTransfer.value.orEmptyItems().map { item ->
                if (item.relativePath == relativePath && !item.isDirectory) {
                    item.copy(selected = selected)
                } else {
                    item
                }
            }
        )
    }

    fun renameIncomingTransferItem(relativePath: String, proposedName: String) {
        val sanitized = PathSanitizer.sanitizeFileName(proposedName)
        _incomingTransfer.value = _incomingTransfer.value?.copy(
            items = _incomingTransfer.value.orEmptyItems().map { item ->
                if (item.relativePath == relativePath && !item.isDirectory) {
                    item.copy(proposedName = sanitized)
                } else {
                    item
                }
            }
        )
    }

    fun acceptIncomingTransfer() {
        val state = _incomingTransfer.value ?: return
        if (state.senderClosed) {
            _incomingTransfer.value = null
            return
        }
        val acceptedPaths = selectedIncomingPaths(state.items)
        if (acceptedPaths.isEmpty()) {
            push("Select at least one file to receive")
            return
        }

        val renameMap = buildRenameMap(state.items)
        TransferServiceBus.respondIncomingTransfer(
            IncomingTransferDecision(
                accepted = true,
                receiveTreeUri = state.receiveTreeUri,
                renameMap = renameMap,
                acceptedPaths = acceptedPaths,
                remoteCancelled = false,
            )
        )
        push("Accepted incoming transfer from ${state.senderName}")
        _incomingTransfer.value = null
    }

    fun declineIncomingTransfer() {
        val state = _incomingTransfer.value ?: return
        if (state.senderClosed) {
            _incomingTransfer.value = null
            return
        }
        TransferServiceBus.respondIncomingTransfer(
            IncomingTransferDecision(
                accepted = false,
                receiveTreeUri = state.receiveTreeUri,
                renameMap = emptyMap(),
                acceptedPaths = emptySet(),
                reason = "Declined on receiver",
                remoteCancelled = false,
            )
        )
        push("Declined incoming transfer from ${state.senderName}")
        _incomingTransfer.value = null
    }

    fun sendUris(uris: List<Uri>) {
        viewModelScope.launch { queue.enqueue(OutgoingTransferQueue.Task.UriBatch(settingsRepo.settingsFlow.first(), uris)) }
    }

    fun addUrisToSelection(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        val existingByUri = _sendSelection.value.associateBy { it.uriString }.toMutableMap()
        uris.forEach { uri ->
            val uriString = uri.toString()
            if (existingByUri.containsKey(uriString)) return@forEach
            val (fileName, sizeBytes) = queryUriMeta(resolver, uri)
            existingByUri[uriString] = SendSelectionItemUi(
                uriString = uriString,
                fileName = fileName,
                sizeBytes = sizeBytes,
            )
        }
        _sendSelection.value = existingByUri.values.toList()
    }

    fun removeSendSelectionItem(uriString: String) {
        _sendSelection.value = _sendSelection.value.filterNot { it.uriString == uriString }
    }

    fun clearSendSelection() {
        _sendSelection.value = emptyList()
    }

    fun sendSelectionToPeer(peer: DiscoveredPeer) {
        val selected = _sendSelection.value
        if (selected.isEmpty()) {
            push("Add files to selection first")
            return
        }
        val uris = selected.map { Uri.parse(it.uriString) }
        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            val targetSettings = settings.copy(pcHost = peer.ip, pcPort = peer.port)
            pendingSenderSelectionRestore = selected
            queue.enqueue(OutgoingTransferQueue.Task.UriBatch(targetSettings, uris))
            push("Queued ${uris.size} file(s) for ${peer.deviceName}")
            _sendSelection.value = emptyList()
        }
    }

    fun sendFolderTree(treeUri: Uri) {
        viewModelScope.launch { queue.enqueue(OutgoingTransferQueue.Task.FolderTree(settingsRepo.settingsFlow.first(), treeUri)) }
    }

    fun onSenderRequestAction() {
        val current = _senderRequest.value ?: return
        if (current.waiting) {
            cancelTransfer()
            return
        }
        _senderRequest.value = null
    }

    fun acknowledgeIncomingTransferSenderClosed() {
        _incomingTransfer.value = null
    }

    fun openReceivedSessionFile(openUri: String?) {
        if (openUri.isNullOrBlank()) {
            push("No file link available to open")
            return
        }
        val app = getApplication<Application>()
        val uri = Uri.parse(openUri)
        val mime = app.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            app.startActivity(Intent.createChooser(intent, "Open file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            push("Could not open the received file")
        }
    }

    fun completeReceiveSession() {
        val current = _receiveSession.value ?: return
        if (current.active) {
            return
        }
        _receiveSession.value = null
    }

    private suspend fun handleSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        addUrisToSelection(uris)
        push("Share received: added ${uris.size} file(s) to selection")
    }

    private fun queryUriMeta(resolver: android.content.ContentResolver, uri: Uri): Pair<String, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                return Pair(name?.ifBlank { null } ?: "file.bin", size.coerceAtLeast(0L))
            }
        }
        return Pair("file.bin", 0L)
    }

    override fun onCleared() {
        cancelTransfer()
        sfxPlayer.release()
        super.onCleared()
    }

    private fun IncomingTransferRequest.toUiState(): IncomingTransferUiState {
        return IncomingTransferUiState(
            transferId = transferId,
            senderName = senderName,
            receiveTreeUri = receiveTreeUri,
            receiveTargetLabel = receiveTargetLabel,
            items = items.map { it.toUiItem() },
        )
    }

    private fun IncomingTransferItem.toUiItem(): IncomingTransferUiItem {
        return IncomingTransferUiItem(
            relativePath = relativePath,
            fileName = fileName,
            parentPath = parentPath,
            sizeBytes = sizeBytes,
            isDirectory = isDirectory,
            selected = selected,
            proposedName = PathSanitizer.sanitizeFileName(proposedName),
        )
    }

    private fun IncomingTransferUiState?.orEmptyItems(): List<IncomingTransferUiItem> {
        return this?.items ?: emptyList()
    }

    private fun selectedIncomingPaths(items: List<IncomingTransferUiItem>): Set<String> {
        val selectedFiles = items
            .filter { !it.isDirectory && it.selected }
            .mapTo(linkedSetOf()) { it.relativePath }
        if (selectedFiles.isEmpty()) {
            return emptySet()
        }

        val directoryPaths = items
            .filter { it.isDirectory }
            .mapTo(hashSetOf()) { it.relativePath }
        val resolved = linkedSetOf<String>()
        resolved.addAll(selectedFiles)
        selectedFiles.forEach { filePath ->
            var current = filePath.substringBeforeLast('/', "")
            while (current.isNotBlank()) {
                if (current in directoryPaths) {
                    resolved.add(current)
                }
                current = current.substringBeforeLast('/', "")
            }
        }
        return resolved
    }

    private fun buildRenameMap(items: List<IncomingTransferUiItem>): Map<String, String> {
        val renameMap = linkedMapOf<String, String>()
        items.filter { !it.isDirectory && it.selected }.forEach { item ->
            val targetName = PathSanitizer.sanitizeFileName(item.proposedName)
            if (targetName == item.fileName) {
                return@forEach
            }
            val targetRelativePath = if (item.parentPath.isBlank()) {
                targetName
            } else {
                "${item.parentPath}/$targetName"
            }
            renameMap[item.relativePath] = targetRelativePath
        }
        return renameMap
    }

    private fun handleSenderRequestSignal(text: String) {
        val signal = text.substringAfter("SENDER_REQUEST:", "")
        when (signal) {
            "waiting" -> {
                val settings = _settings.value
                val targetPeer = _discoveredPeers.value.firstOrNull { it.ip == settings.pcHost && it.port == settings.pcPort }
                _senderRequest.value = SenderRequestUiState(
                    senderName = settings.deviceName.ifBlank { "Android device" },
                    senderId = settings.deviceId.take(8),
                    receiverName = targetPeer?.deviceName ?: settings.pcHost.ifBlank { "Receiver" },
                    receiverId = targetPeer?.deviceId?.take(8) ?: settings.pcPort.toString(),
                    waiting = true,
                )
            }
            "accepted" -> {
                pendingSenderSelectionRestore = emptyList()
                _senderRequest.value = null
            }
            "rejected" -> {
                restorePendingSenderSelection()
                val current = _senderRequest.value
                if (current != null) {
                    _senderRequest.value = current.copy(waiting = false)
                    return
                }
                val settings = _settings.value
                val targetPeer = _discoveredPeers.value.firstOrNull { it.ip == settings.pcHost && it.port == settings.pcPort }
                _senderRequest.value = SenderRequestUiState(
                    senderName = settings.deviceName.ifBlank { "Android device" },
                    senderId = settings.deviceId.take(8),
                    receiverName = targetPeer?.deviceName ?: settings.pcHost.ifBlank { "Receiver" },
                    receiverId = targetPeer?.deviceId?.take(8) ?: settings.pcPort.toString(),
                    waiting = false,
                )
            }
        }
    }

    private fun handleIncomingTransferSignal(text: String) {
        val signal = text.substringAfter("INCOMING_TRANSFER:", "")
        if (!signal.startsWith("sender_closed:")) {
            return
        }
        val transferId = signal.substringAfter("sender_closed:", "")
        val current = _incomingTransfer.value ?: return
        if (transferId.isNotBlank() && current.transferId != transferId) {
            return
        }
        _incomingTransfer.value = current.copy(senderClosed = true)
        push("Sender closed the session")
    }

    private fun updateReceiveSessionProgress(relativePath: String, sent: Long, total: Long) {
        val current = _receiveSession.value ?: return
        val updated = current.files.map { file ->
            if (file.relativePath != relativePath) {
                file
            } else {
                val size = if (file.sizeBytes > 0L) file.sizeBytes else total
                val received = sent.coerceAtLeast(0L)
                file.copy(
                    sizeBytes = if (size > 0L) size else file.sizeBytes,
                    receivedBytes = received,
                    done = size > 0L && received >= size
                )
            }
        }
        _receiveSession.value = current.copy(files = updated)
    }

    private fun handleReceiveSessionSignal(text: String) {
        val payloadRaw = text.substringAfter("RECEIVE_SESSION:", "").trim()
        if (payloadRaw.isBlank()) return
        val payload = runCatching { JSONObject(payloadRaw) }.getOrElse { return }
        when (payload.optString("type")) {
            "start" -> {
                val filesArray = payload.optJSONArray("files") ?: JSONArray()
                val files = mutableListOf<ReceiveSessionFileUi>()
                for (i in 0 until filesArray.length()) {
                    val item = filesArray.optJSONObject(i) ?: continue
                    val rel = item.optString("relative_path", "").trim()
                    if (rel.isBlank()) continue
                    files += ReceiveSessionFileUi(
                        relativePath = rel,
                        fileName = item.optString("file_name", rel.substringAfterLast('/')),
                        sizeBytes = item.optLong("size", 0L),
                        receivedBytes = 0L,
                        done = false,
                        openUri = null,
                    )
                }
                _receiveSession.value = ReceiveSessionUiState(
                    transferId = payload.optString("transfer_id"),
                    targetLabel = payload.optString("target_label", "Receive folder"),
                    files = files,
                    active = true,
                    finished = false,
                    cancelled = false,
                )
            }
            "file_done" -> {
                val current = _receiveSession.value ?: return
                val rel = payload.optString("relative_path", "").trim()
                if (rel.isBlank()) return
                val size = payload.optLong("size", 0L)
                val openUri = payload.optString("open_uri").ifBlank { null }
                val doneName = payload.optString("file_name").ifBlank { rel.substringAfterLast('/') }
                val updated = current.files.map { file ->
                    if (file.relativePath != rel) file else file.copy(
                        fileName = doneName,
                        sizeBytes = if (size > 0L) size else file.sizeBytes,
                        receivedBytes = if (size > 0L) size else file.receivedBytes,
                        done = true,
                        openUri = openUri ?: file.openUri,
                    )
                }
                _receiveSession.value = current.copy(files = updated)
            }
            "complete" -> {
                _receiveSession.value = _receiveSession.value?.copy(active = false, finished = true, cancelled = false)
            }
            "cancelled" -> {
                _receiveSession.value = _receiveSession.value?.copy(active = false, finished = false, cancelled = true)
            }
        }
    }

    private fun restorePendingSenderSelection() {
        if (pendingSenderSelectionRestore.isEmpty()) {
            return
        }
        val merged = LinkedHashMap<String, SendSelectionItemUi>()
        pendingSenderSelectionRestore.forEach { merged[it.uriString] = it }
        _sendSelection.value.forEach { merged[it.uriString] = it }
        _sendSelection.value = merged.values.toList()
        pendingSenderSelectionRestore = emptyList()
    }
}
