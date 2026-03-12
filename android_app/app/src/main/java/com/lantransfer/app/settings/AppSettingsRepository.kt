package com.lantransfer.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lantransfer.app.core.AppConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lan_transfer_settings")

data class AppSettings(
    val deviceId: String,
    val deviceName: String,
    val pcHost: String,
    val pcPort: Int,
    val localPort: Int,
    val autoScreenshotSync: Boolean,
    val receiveTreeUri: String?
)

class AppSettingsRepository(private val context: Context) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val PC_HOST = stringPreferencesKey("pc_host")
        val PC_PORT = intPreferencesKey("pc_port")
        val LOCAL_PORT = intPreferencesKey("local_port")
        val AUTO_SS = booleanPreferencesKey("auto_ss")
        val RECEIVE_TREE = stringPreferencesKey("receive_tree")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            deviceId = p[Keys.DEVICE_ID] ?: java.util.UUID.randomUUID().toString(),
            deviceName = p[Keys.DEVICE_NAME] ?: android.os.Build.MODEL,
            pcHost = p[Keys.PC_HOST] ?: "",
            pcPort = p[Keys.PC_PORT] ?: AppConstants.DEFAULT_PORT,
            localPort = p[Keys.LOCAL_PORT] ?: p[Keys.PC_PORT] ?: AppConstants.DEFAULT_PORT,
            autoScreenshotSync = p[Keys.AUTO_SS] ?: false,
            receiveTreeUri = p[Keys.RECEIVE_TREE]
        )
    }

    suspend fun persistGeneratedDeviceIdIfNeeded(settings: AppSettings) {
        context.dataStore.edit { p -> if (p[Keys.DEVICE_ID].isNullOrBlank()) p[Keys.DEVICE_ID] = settings.deviceId }
    }

    suspend fun updateConnection(host: String, port: Int) {
        context.dataStore.edit {
            it[Keys.PC_HOST] = host
            it[Keys.PC_PORT] = port
        }
    }

    suspend fun updateLocalPort(port: Int) {
        context.dataStore.edit {
            it[Keys.LOCAL_PORT] = port
        }
    }

    suspend fun updateDeviceName(name: String) {
        val cleaned = name.trim().take(42)
        context.dataStore.edit {
            it[Keys.DEVICE_NAME] = cleaned.ifBlank { android.os.Build.MODEL }
        }
    }

    suspend fun setAutoScreenshot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SS] = enabled }
    }

    suspend fun setReceiveTree(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.RECEIVE_TREE) else it[Keys.RECEIVE_TREE] = uri
        }
    }
}
