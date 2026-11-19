package com.example.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.transfer.TransportPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shareMeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shareme_device_prefs"
)

data class DevicePreferences(
    val deviceName: String,
    val avatarColorIndex: Int,
    val autoAcceptIncoming: Boolean,
    val preferWifiDirect: Boolean,
    val transportPreference: TransportPreference = TransportPreference.AUTO_BOTH
)

class DevicePreferencesRepository(private val context: Context) {

    private object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val AVATAR_COLOR_INDEX = intPreferencesKey("avatar_color_index")
        val AUTO_ACCEPT = booleanPreferencesKey("auto_accept_incoming")
        val PREFER_WIFI_DIRECT = booleanPreferencesKey("prefer_wifi_direct")
        val TRANSPORT_PREFERENCE = stringPreferencesKey("transport_preference")
    }

    val preferences: Flow<DevicePreferences> = context.shareMeDataStore.data.map { prefs ->
        val rawTransport = prefs[Keys.TRANSPORT_PREFERENCE]
        val transport = runCatching {
            if (rawTransport != null) TransportPreference.valueOf(rawTransport) else TransportPreference.AUTO_BOTH
        }.getOrDefault(TransportPreference.AUTO_BOTH)

        DevicePreferences(
            deviceName = prefs[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() } ?: defaultDeviceName(),
            avatarColorIndex = (prefs[Keys.AVATAR_COLOR_INDEX] ?: 0).coerceIn(0, 5),
            autoAcceptIncoming = prefs[Keys.AUTO_ACCEPT] ?: false,
            preferWifiDirect = prefs[Keys.PREFER_WIFI_DIRECT] ?: true,
            transportPreference = transport
        )
    }

    suspend fun setDeviceName(name: String) {
        val cleaned = name.trim().take(28).ifBlank { defaultDeviceName() }
        context.shareMeDataStore.edit { it[Keys.DEVICE_NAME] = cleaned }
    }

    suspend fun setAvatarColorIndex(index: Int) {
        context.shareMeDataStore.edit { it[Keys.AVATAR_COLOR_INDEX] = index.coerceIn(0, 5) }
    }

    suspend fun setAutoAcceptIncoming(enabled: Boolean) {
        context.shareMeDataStore.edit { it[Keys.AUTO_ACCEPT] = enabled }
    }

    suspend fun setPreferWifiDirect(enabled: Boolean) {
        context.shareMeDataStore.edit { it[Keys.PREFER_WIFI_DIRECT] = enabled }
    }

    suspend fun setTransportPreference(preference: TransportPreference) {
        context.shareMeDataStore.edit { it[Keys.TRANSPORT_PREFERENCE] = preference.name }
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.isNotEmpty()) model.take(24) else "Android Phone"
    }
}
