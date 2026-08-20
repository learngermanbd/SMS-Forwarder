package com.pulserelay.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pulserelay.app.domain.DuplicateDetector
import com.pulserelay.app.domain.Provider

class LocalConfigStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pulse_relay_secure_config",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value.trim()).apply()

    var channelId: String
        get() = prefs.getString(KEY_CHANNEL_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CHANNEL_ID, value.trim()).apply()

    var relayEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RELAY_ENABLED, value).apply()

    var redactSensitiveData: Boolean
        get() = prefs.getBoolean(KEY_REDACT_SENSITIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_REDACT_SENSITIVE, value).apply()

    var enabledProviders: Set<Provider>
        get() = prefs.getStringSet(KEY_PROVIDERS, Provider.entries.map { it.name }.toSet())
            .orEmpty()
            .mapNotNull { name -> Provider.entries.firstOrNull { it.name == name } }
            .toSet()
        set(value) = prefs.edit().putStringSet(KEY_PROVIDERS, value.map { it.name }.toSet()).apply()

    var seenTransactionIds: Set<String>
        get() = prefs.getStringSet(KEY_SEEN_TRX, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_SEEN_TRX, value).apply()

    var scamAlertCount: Int
        get() = prefs.getInt(KEY_SCAM_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SCAM_COUNT, value).apply()

    /** Records a transaction ID and reports whether it was already seen. */
    fun recordTransactionId(id: String): Boolean {
        val seen = seenTransactionIds
        val (isDuplicate, updated) = DuplicateDetector.register(id, seen)
        if (!isDuplicate) seenTransactionIds = updated
        return isDuplicate
    }

    fun incrementScamAlertCount() {
        scamAlertCount = scamAlertCount + 1
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_RELAY_ENABLED = "relay_enabled"
        const val KEY_REDACT_SENSITIVE = "redact_sensitive"
        const val KEY_PROVIDERS = "enabled_providers"
        const val KEY_SEEN_TRX = "seen_transaction_ids"
        const val KEY_SCAM_COUNT = "scam_alert_count"
    }
}
