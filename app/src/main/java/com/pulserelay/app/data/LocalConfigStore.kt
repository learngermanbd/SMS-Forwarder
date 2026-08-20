package com.pulserelay.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pulserelay.app.domain.DuplicateDetector
import org.json.JSONArray
import org.json.JSONObject

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

    var blockOtpContent: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_OTP, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_OTP, value).apply()

    var hideBalance: Boolean
        get() = prefs.getBoolean(KEY_HIDE_BALANCE, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_BALANCE, value).apply()

    var selectedSenders: Set<String>
        get() = prefs.getStringSet(KEY_SENDERS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_SENDERS, value).apply()

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

    fun activityHistory(): List<ActivityEntry> {
        val raw = prefs.getString(KEY_ACTIVITY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ActivityEntry(
                            timestamp = obj.getLong("t"),
                            provider = obj.getString("p"),
                            summary = obj.getString("s"),
                            isScam = obj.getBoolean("scam"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordActivity(entry: ActivityEntry) {
        val entries = (listOf(entry) + activityHistory()).take(MAX_ACTIVITY_ENTRIES)
        prefs.edit().putString(KEY_ACTIVITY, encodeActivity(entries).toString()).apply()
    }

    fun clearActivity() = prefs.edit().remove(KEY_ACTIVITY).apply()

    /** Serializes all local data so it can be moved to another device. */
    fun exportBackup(): String {
        val root = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("botToken", botToken)
            .put("channelId", channelId)
            .put("relayEnabled", relayEnabled)
            .put("redactSensitiveData", redactSensitiveData)
            .put("blockOtpContent", blockOtpContent)
            .put("hideBalance", hideBalance)
            .put("selectedSenders", JSONArray(selectedSenders.toList()))
            .put("seenTransactionIds", JSONArray(seenTransactionIds.toList()))
            .put("scamAlertCount", scamAlertCount)
            .put("activity", encodeActivity(activityHistory()))
        return root.toString(2)
    }

    /** Restores local data from a previously exported backup. */
    fun importBackup(json: String): Result<Unit> = runCatching {
        val root = JSONObject(json)
        botToken = root.optString("botToken", "")
        channelId = root.optString("channelId", "")
        relayEnabled = root.optBoolean("relayEnabled", false)
        redactSensitiveData = root.optBoolean("redactSensitiveData", true)
        blockOtpContent = root.optBoolean("blockOtpContent", true)
        hideBalance = root.optBoolean("hideBalance", false)
        selectedSenders = readStringArray(root.optJSONArray("selectedSenders"))
        seenTransactionIds = readStringArray(root.optJSONArray("seenTransactionIds"))
        scamAlertCount = root.optInt("scamAlertCount", 0)
        root.optJSONArray("activity")?.let { array ->
            prefs.edit().putString(KEY_ACTIVITY, array.toString()).apply()
        }
    }

    private fun readStringArray(array: JSONArray?): Set<String> =
        array?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet()

    private fun encodeActivity(entries: List<ActivityEntry>): JSONArray =
        JSONArray().apply {
            entries.forEach { item ->
                put(
                    JSONObject()
                        .put("t", item.timestamp)
                        .put("p", item.provider)
                        .put("s", item.summary)
                        .put("scam", item.isScam),
                )
            }
        }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_RELAY_ENABLED = "relay_enabled"
        const val KEY_REDACT_SENSITIVE = "redact_sensitive"
        const val KEY_BLOCK_OTP = "block_otp"
        const val KEY_HIDE_BALANCE = "hide_balance"
        const val KEY_SENDERS = "selected_senders"
        const val KEY_SEEN_TRX = "seen_transaction_ids"
        const val KEY_SCAM_COUNT = "scam_alert_count"
        const val KEY_ACTIVITY = "activity_history"
        const val MAX_ACTIVITY_ENTRIES = 50
        const val BACKUP_VERSION = 1
    }
}
