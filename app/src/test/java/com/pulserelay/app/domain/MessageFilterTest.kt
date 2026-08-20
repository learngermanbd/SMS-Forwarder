package com.pulserelay.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterTest {
    @Test
    fun `accepts selected sender and redacts phone and long number`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("bKash", "Cash In BDT 500 from 01712345678. Ref 123456789"),
            FilterSettings(enabledSenders = setOf("bkash")),
        )

        assertTrue(result.accepted)
        assertEquals(Provider.BKASH, result.provider)
        assertTrue(result.safeBody!!.contains("[phone redacted]"))
        assertTrue(result.safeBody!!.contains("[number redacted]"))
    }

    @Test
    fun `rejects sender that is not selected`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("Unknown", "Cash In BDT 500"),
            FilterSettings(enabledSenders = setOf("bkash")),
        )
        assertFalse(result.accepted)
        assertEquals("Sender is not selected", result.reason)
    }

    @Test
    fun `rejects everything when no senders are selected`() {
        val result = MessageFilter.evaluate(IncomingMessage("bKash", "Cash In BDT 500"))
        assertFalse(result.accepted)
        assertEquals("No senders selected", result.reason)
    }

    @Test
    fun `rejects otp content even for selected sender`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("Nagad", "Your OTP is 123456"),
            FilterSettings(enabledSenders = setOf("nagad")),
        )
        assertFalse(result.accepted)
        assertEquals("Possible OTP or PIN content", result.reason)
    }
}
