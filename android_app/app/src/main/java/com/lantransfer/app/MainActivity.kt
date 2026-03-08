package com.lantransfer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lantransfer.app.ui.HyperDropTheme
import com.lantransfer.app.ui.IncomingShareBus
import com.lantransfer.app.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        handleIncomingShare(intent)
        setContent {
            HyperDropTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent == null) return

        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                listOfNotNull(intent.getUriExtra(Intent.EXTRA_STREAM))
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getUriListExtra(Intent.EXTRA_STREAM)
            }

            else -> emptyList()
        }

        if (uris.isNotEmpty()) {
            IncomingShareBus.publish(uris)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.getUriExtra(key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Uri::class.java)
        } else {
            getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.getUriListExtra(key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, Uri::class.java) ?: emptyList()
        } else {
            getParcelableArrayListExtra<Uri>(key) ?: emptyList()
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
