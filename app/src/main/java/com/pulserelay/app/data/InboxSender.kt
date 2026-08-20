package com.pulserelay.app.data

/** A unique sender found in the SMS inbox, with a short message preview for recognition. */
data class InboxSender(
    val address: String,
    val messageCount: Int,
    val preview: String,
)
