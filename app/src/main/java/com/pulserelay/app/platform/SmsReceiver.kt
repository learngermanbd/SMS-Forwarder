package com.pulserelay.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pulserelay.app.data.LocalConfigStore
import com.pulserelay.app.domain.FilterSettings
import com.pulserelay.app.domain.IncomingMessage
import com.pulserelay.app.domain.MessageFilter

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val incoming = IncomingMessage(
            sender = messages.first().originatingAddress.orEmpty(),
            body = messages.joinToString(separator = "") { it.messageBody.orEmpty() },
        )
        val config = LocalConfigStore(context)
        if (!config.relayEnabled) return
        val decision = MessageFilter.evaluate(
            incoming,
            FilterSettings(
                enabledProviders = config.enabledProviders,
                redactSensitiveData = config.redactSensitiveData,
            ),
        )
        if (!decision.accepted || decision.provider == null || decision.safeBody == null) return

        val payload = Data.Builder()
            .putString(RelayWorker.KEY_PROVIDER, decision.provider.label)
            .putString(RelayWorker.KEY_BODY, decision.safeBody)
            .putLong(RelayWorker.KEY_RECEIVED_AT, incoming.receivedAt)
            .build()
        val request = OneTimeWorkRequestBuilder<RelayWorker>()
            .setInputData(payload)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
