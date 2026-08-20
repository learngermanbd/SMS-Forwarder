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
        assertTrue(result.safeBody!!.contains("[phone hidden]"))
        assertTrue(result.safeBody!!.contains("[number hidden]"))
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
    fun `rejects otp content by default`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("Nagad", "Your OTP is 123456"),
            FilterSettings(enabledSenders = setOf("nagad")),
        )
        assertFalse(result.accepted)
        assertEquals("Possible OTP, PIN, or password content", result.reason)
    }

    @Test
    fun `allows otp content when block option is off`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("Nagad", "Your OTP is 123456"),
            FilterSettings(enabledSenders = setOf("nagad"), blockOtpContent = false),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `hides balance when enabled`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("bKash", "Cash In BDT 500.00 from 01712345678"),
            FilterSettings(enabledSenders = setOf("bkash"), hideBalance = true),
        )
        assertTrue(result.accepted)
        assertTrue(result.safeBody!!.contains("[balance hidden]"))
        assertFalse(result.safeBody!!.contains("500.00"))
    }
}
