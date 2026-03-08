package com.lantransfer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
    private val discoveryResponder = DiscoveryResponder(
        scope = scope,
        deviceIdProvider = { currentSettings?.deviceId.orEmpty() },
        deviceNameProvider = { currentSettings?.deviceName ?: android.os.Build.MODEL },
        tcpPortProvider = { currentSettings?.pcPort ?: AppConstants.DEFAULT_PORT },
        platform = "android"
    )

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        settingsRepo = AppSettingsRepository(this)
        transferEngine = TransferEngine(this)
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
        stopEngine()
        super.onDestroy()
    }

    private fun startEngine() {
        if (running) return
        running = true
        startForeground(AppConstants.NOTIFICATION_ID, buildNotification("Transfer service running"))

        scope.launch {
            val initialSettings = settingsRepo.settingsFlow.first()
            settingsRepo.persistGeneratedDeviceIdIfNeeded(initialSettings)
            currentSettings = initialSettings
            discoveryResponder.start()
            settingsJob?.cancel()
            settingsJob = scope.launch {
                settingsRepo.settingsFlow.collectLatest { updated ->
                    currentSettings = updated
                }
            }

            retryWithBackoff {
                transferEngine.runServer(initialSettings, { TransferServiceBus.emit(it) }) { running }
            }
        }
    }

    private fun stopEngine() {
        running = false
        settingsJob?.cancel()
        settingsJob = null
        discoveryResponder.stop()
        currentSettings = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
