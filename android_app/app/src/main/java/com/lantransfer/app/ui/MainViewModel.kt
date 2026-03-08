package com.lantransfer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lantransfer.app.network.DiscoveredPeer
import com.lantransfer.app.network.DiscoveryResponder
import com.lantransfer.app.network.LanDiscovery
import com.lantransfer.app.service.TransferForegroundService
import com.lantransfer.app.service.TransferServiceBus
import com.lantransfer.app.settings.AppSettings
import com.lantransfer.app.settings.AppSettingsRepository
import com.lantransfer.app.transfer.OutgoingTransferQueue
import com.lantransfer.app.transfer.TransferEngine
import com.lantransfer.app.util.SfxPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = AppSettingsRepository(app)
    private val transferEngine = TransferEngine(app)
    private val sfxPlayer = SfxPlayer(app)
    private val discovery = LanDiscovery(
        deviceIdProvider = { _settings.value.deviceId },
        deviceNameProvider = { _settings.value.deviceName },
        tcpPortProvider = { _settings.value.pcPort },
        platform = "android"
    )
    private val discoveryResponder = DiscoveryResponder(
        scope = viewModelScope,
        deviceIdProvider = { _settings.value.deviceId },
        deviceNameProvider = { _settings.value.deviceName },
        tcpPortProvider = { _settings.value.pcPort },
        platform = "android"
    )

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _settings = MutableStateFlow(
        AppSettings("", "", "", 54545, "123456", false, null)
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
        discoveryResponder.start()
        viewModelScope.launch {
            TransferServiceBus.events.collectLatest { push(it) }
        }
        viewModelScope.launch {
            IncomingShareBus.uris.collectLatest { uris ->
                handleSharedUris(uris)
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
        _events.value = (_events.value + text).takeLast(200)
        sfxPlayer.playForEvent(text)
    }

    fun startService() {
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, TransferForegroundService::class.java).apply { action = TransferForegroundService.ACTION_START })
    }

    fun stopService() {
        val app = getApplication<Application>()
        app.startService(Intent(app, TransferForegroundService::class.java).apply { action = TransferForegroundService.ACTION_STOP })
    }

    fun updateConnection(host: String, port: Int, pairingCode: String) {
        viewModelScope.launch { settingsRepo.updateConnection(host, port, pairingCode) }
    }

    fun updatePairingCode(pairingCode: String) {
        viewModelScope.launch { settingsRepo.updatePairingCode(pairingCode) }
    }

    fun connectToPeer(peer: DiscoveredPeer) {
        val code = _settings.value.pairingCode
        viewModelScope.launch {
            settingsRepo.updateConnection(peer.ip, peer.port, code)
            // Ensure receiver stays reachable after target/port changes.
            startService()
            push("Connected target: ${peer.deviceName} (${peer.ip}:${peer.port})")
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
            if (!silent) push("Found ${peers.size} device(s)")
        }
    }

    fun setReceiveTree(uri: Uri?) {
        viewModelScope.launch { settingsRepo.setReceiveTree(uri?.toString()) }
    }

    fun sendUris(uris: List<Uri>) {
        viewModelScope.launch { queue.enqueue(OutgoingTransferQueue.Task.UriBatch(settingsRepo.settingsFlow.first(), uris)) }
    }

    fun sendFolderTree(treeUri: Uri) {
        viewModelScope.launch { queue.enqueue(OutgoingTransferQueue.Task.FolderTree(settingsRepo.settingsFlow.first(), treeUri)) }
    }

    private suspend fun handleSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val settings = settingsRepo.settingsFlow.first()
        if (settings.pcHost.isBlank()) {
            push("Share received but no PC selected. Choose a target device first.")
            return
        }
        queue.enqueue(OutgoingTransferQueue.Task.UriBatch(settings, uris))
        push("Share received: queued ${uris.size} image(s)")
    }

    override fun onCleared() {
        discoveryResponder.stop()
        sfxPlayer.release()
        super.onCleared()
    }
}
