package com.pulserelay.app.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulserelay.app.data.LocalConfigStore
import com.pulserelay.app.network.TelegramBotClient

class RelayWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val config = LocalConfigStore(applicationContext)
        if (!config.relayEnabled) return Result.success()

        val provider = inputData.getString(KEY_PROVIDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        if (provider.isBlank() || body.isBlank()) return Result.failure()

        val transactionId = inputData.getString(KEY_TRANSACTION_ID).orEmpty()
        val isScamAlert = inputData.getBoolean(KEY_IS_SCAM_ALERT, false)

        val text = if (isScamAlert) {
            buildString {
                appendLine("🚨 SCAM ALERT — duplicate transaction ID")
                appendLine("PulseRelay • $provider")
                if (transactionId.isNotBlank()) appendLine("TrxID: $transactionId")
                append("This receipt was received before and may be a fake payment confirmation.")
            }
        } else {
            buildString {
                appendLine("PulseRelay • $provider")
                if (transactionId.isNotBlank()) appendLine("TrxID: $transactionId")
                append(body)
            }
        }
        return TelegramBotClient()
            .sendMessage(config.botToken, config.channelId, text)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_BODY = "safe_body"
        const val KEY_RECEIVED_AT = "received_at"
        const val KEY_TRANSACTION_ID = "transaction_id"
        const val KEY_IS_SCAM_ALERT = "is_scam_alert"
    }
}
