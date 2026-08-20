package com.pulserelay.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TelegramBotClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun sendMessage(botToken: String, channelId: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(botToken.isNotBlank()) { "Telegram bot token is missing" }
                require(channelId.isNotBlank()) { "Telegram channel ID is missing" }
                require(text.isNotBlank()) { "Message is empty" }

                val url = "https://api.telegram.org/bot$botToken/sendMessage".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("chat_id", channelId)
                    .addQueryParameter("text", text)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(ByteArray(0).toRequestBody())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    val ok = response.isSuccessful && JSONObject(payload).optBoolean("ok", false)
                    check(ok) { "Telegram rejected the message (${response.code})" }
                }
            }
        }
}
