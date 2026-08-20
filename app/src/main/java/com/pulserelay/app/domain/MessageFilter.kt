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
    val enabledProviders: Set<Provider> = Provider.entries.toSet(),
    val redactSensitiveData: Boolean = true,
)

data class FilterDecision(
    val accepted: Boolean,
    val provider: Provider? = null,
    val safeBody: String? = null,
    val reason: String? = null,
)

object MessageFilter {
    private val otpPattern = Regex("(?i)\\b(otp|one[- ]time|verification|verify|pin|passcode|security code)\\b")
    private val phonePattern = Regex("(?<!\\d)(?:\\+?880|0)1[3-9]\\d{8}(?!\\d)")
    private val longNumberPattern = Regex("(?<!\\d)\\d{6,}(?!\\d)")

    fun evaluate(message: IncomingMessage, settings: FilterSettings = FilterSettings()): FilterDecision {
        val sender = message.sender.lowercase().replace(" ", "")
        val provider = Provider.entries.firstOrNull { providerEntry ->
            providerEntry.aliases.any(sender::contains)
        }

        if (provider == null) {
            return FilterDecision(accepted = false, reason = "Sender is not an approved wallet")
        }
        if (provider !in settings.enabledProviders) {
            return FilterDecision(accepted = false, reason = "Provider is disabled")
        }
        if (otpPattern.containsMatchIn(message.body)) {
            return FilterDecision(accepted = false, provider = provider, reason = "Possible OTP or PIN content")
        }

        val safeBody = if (settings.redactSensitiveData) {
            message.body
                .replace(phonePattern, "[phone redacted]")
                .replace(longNumberPattern, "[number redacted]")
                .trim()
        } else {
            message.body.trim()
        }

        if (safeBody.isBlank()) {
            return FilterDecision(accepted = false, provider = provider, reason = "Message has no safe content")
        }
        return FilterDecision(accepted = true, provider = provider, safeBody = safeBody)
    }
}
