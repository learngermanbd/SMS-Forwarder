package com.pulserelay.app.data

/** A redacted record of a relayed or flagged message. Raw SMS content is never stored. */
data class ActivityEntry(
    val timestamp: Long,
    val provider: String,
    val summary: String,
    val isScam: Boolean,
)
