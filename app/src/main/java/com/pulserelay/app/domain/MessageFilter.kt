package com.pulserelay.app.domain

/** A notification received from the device before it leaves the device. */
data class IncomingMessage(
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

enum class Provider(
    val label: String,
    val shortLabel: String,
    val aliases: Set<String>,
) {
    BKASH("bKash", "bK", setOf("bkash", "b-kash", "16247")),
    NAGAD("Nagad", "NG", setOf("nagad", "16167")),
    ROCKET("Rocket", "RK", setOf("rocket", "dbbl", "dutch-bangla", "16216")),
}

data class FilterSettings(
    val enabledSenders: Set<String> = emptySet(),
    val redactSensitiveData: Boolean = true,
    val blockOtpContent: Boolean = true,
    val hideBalance: Boolean = false,
)

data class FilterDecision(
    val accepted: Boolean,
    val provider: Provider? = null,
    val safeBody: String? = null,
    val reason: String? = null,
)

object MessageFilter {
    private val otpPattern = Regex(
        "(?i)\\b(otp|one[- ]time|verification|verify|pin|passcode|password|login code|security code)\\b",
    )
    private val phonePattern = Regex("(?<!\\d)(?:\\+?880|0)1[3-9]\\d{8}(?!\\d)")
    private val longNumberPattern = Regex("(?<!\\d)\\d{6,}(?!\\d)")
    private val balancePattern = Regex("(?i)\\b(?:bdt|tk|taka|৳)\\s*[\\d,]+(?:\\.\\d+)?")

    fun normalizeSender(sender: String): String =
        sender.trim().lowercase().replace(" ", "")

    /** Best-effort wallet brand detection, used only for labeling and quick-select. */
    fun detectProvider(sender: String): Provider? {
        val normalized = normalizeSender(sender)
        return Provider.entries.firstOrNull { entry -> entry.aliases.any(normalized::contains) }
    }

    fun evaluate(message: IncomingMessage, settings: FilterSettings = FilterSettings()): FilterDecision {
        val sender = normalizeSender(message.sender)
        val provider = detectProvider(message.sender)
        val allowed = settings.enabledSenders.map(::normalizeSender)

        if (allowed.isEmpty()) {
            return FilterDecision(accepted = false, reason = "No senders selected")
        }
        if (sender !in allowed) {
            return FilterDecision(accepted = false, provider = provider, reason = "Sender is not selected")
        }
        if (settings.blockOtpContent && otpPattern.containsMatchIn(message.body)) {
            return FilterDecision(accepted = false, provider = provider, reason = "Possible OTP, PIN, or password content")
        }

        var safeBody = message.body.trim()
        if (settings.redactSensitiveData) {
            safeBody = safeBody
                .replace(phonePattern, "[phone hidden]")
                .replace(longNumberPattern, "[number hidden]")
        }
        if (settings.hideBalance) {
            safeBody = safeBody.replace(balancePattern, "[balance hidden]")
        }
        safeBody = safeBody.trim()

        if (safeBody.isBlank()) {
            return FilterDecision(accepted = false, provider = provider, reason = "Message has no safe content")
        }
        return FilterDecision(accepted = true, provider = provider, safeBody = safeBody)
    }
}
