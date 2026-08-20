package com.pulserelay.app.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsInboxReader(private val context: Context) {

    /** Returns unique senders ordered by how many messages they have sent. */
    suspend fun uniqueSenders(): List<InboxSender> = withContext(Dispatchers.IO) {
        runCatching {
            val projection = arrayOf(
                Telephony.Sms.Inbox.ADDRESS,
                Telephony.Sms.Inbox.BODY,
            )
            val counts = LinkedHashMap<String, Int>()
            val previews = HashMap<String, String>()

            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC",
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex)?.trim().orEmpty()
                    if (address.isBlank()) continue
                    counts[address] = (counts[address] ?: 0) + 1
                    previews.putIfAbsent(address, cursor.getString(bodyIndex).orEmpty())
                }
            }

            counts.entries
                .sortedByDescending { it.value }
                .map { (address, count) ->
                    InboxSender(
                        address = address,
                        messageCount = count,
                        preview = previews[address].orEmpty().take(90),
                    )
                }
        }.getOrElse { emptyList() }
    }
}
