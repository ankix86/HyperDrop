package com.lantransfer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lantransfer.app.MainActivity
import com.lantransfer.app.R
import com.lantransfer.app.core.AppConstants
import com.lantransfer.app.network.DiscoveryResponder
import com.lantransfer.app.settings.AppSettings
import com.lantransfer.app.settings.AppSettingsRepository
import com.lantransfer.app.transfer.TransferEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow

class TransferForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsRepo: AppSettingsRepository
    private lateinit var transferEngine: TransferEngine
    private var settingsJob: Job? = null
    private var currentSettings: AppSettings? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val discoveryResponder = DiscoveryResponder(
        scope = scope,
        deviceIdProvider = { currentSettings?.deviceId.orEmpty() },
        deviceNameProvider = { currentSettings?.deviceName ?: android.os.Build.MODEL },
        tcpPortProvider = { currentSettings?.localPort ?: AppConstants.DEFAULT_PORT },
        platform = "android",
        onPeerOffline = { peerId -> TransferServiceBus.emit("peer_offline:$peerId") }
    )

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        settingsRepo = AppSettingsRepository(this)
        transferEngine = TransferEngine(this)
        multicastLock = runCatching {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wifiManager?.createMulticastLock("hyperdrop-discovery-lock")?.apply {
                setReferenceCounted(false)
            }
        }.getOrNull()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startEngine()
            ACTION_STOP -> stopEngine()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        transferEngine.cancelTransfer()
        stopEngine()
        super.onDestroy()
    }

    private fun startEngine() {
        if (running) return
        running = true
        runCatching { multicastLock?.acquire() }
        TransferServiceBus.setServiceRunning(true)
        startForeground(AppConstants.NOTIFICATION_ID, buildNotification("Receiver ready"))

        scope.launch {
            val initialSettings = settingsRepo.settingsFlow.first()
            settingsRepo.persistGeneratedDeviceIdIfNeeded(initialSettings)
            val finalSettings = settingsRepo.settingsFlow.first()
            currentSettings = finalSettings
            discoveryResponder.acceptingConnections = true
            discoveryResponder.start()
            settingsJob?.cancel()
            settingsJob = scope.launch {
                settingsRepo.settingsFlow.collectLatest { updated ->
                    currentSettings = updated
                }
            }

            // Listen for transfer events and update notification
            scope.launch {
                var isTransferring = false
                TransferServiceBus.events.collectLatest { event ->
                    when {
                        event.startsWith("PROGRESS:") -> {
                            if (!isTransferring) {
                                isTransferring = true
                                updateNotification("Transfer service running")
                            }
                        }
                        event.contains("transfer complete") || event.contains("sent ") -> {
                            isTransferring = false
                            updateNotification("Receiver ready")
                        }
                    }
                }
            }

            retryWithBackoff {
                transferEngine.runServer(
                    settingsProvider = { currentSettings ?: initialSettings },
                    onEvent = { TransferServiceBus.emit(it) },
                    isRunning = { running },
                    incomingTransferHandler = { request ->
                        updateNotification("Incoming transfer request")
                        openIncomingTransferUi()
                        val decision = TransferServiceBus.requestIncomingTransfer(request)
                        updateNotification(if (decision.accepted) "Transfer service running" else "Receiver ready")
                        decision
                    }
                )
            }
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = buildNotification(text)
        manager.notify(AppConstants.NOTIFICATION_ID, notification)
    }

    private fun stopEngine() {
        running = false
        TransferServiceBus.setServiceRunning(false)
        TransferServiceBus.declinePendingIncoming("Receiver stopped")
        settingsJob?.cancel()
        settingsJob = null
        discoveryResponder.stop()
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        currentSettings = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openIncomingTransferUi() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }

    private suspend fun retryWithBackoff(block: suspend () -> Unit) {
        var attempt = 0
        while (running) {
            try {
                block()
                return
            } catch (t: Throwable) {
                TransferServiceBus.emit("service error: ${t.message}")
                val delayMs = (2.0.pow(attempt.toDouble()).toLong().coerceAtMost(30)) * 1000
                delay(delayMs)
                attempt = (attempt + 1).coerceAtMost(8)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(AppConstants.NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.lantransfer.app.action.START"
        const val ACTION_STOP = "com.lantransfer.app.action.STOP"
    }
}
