package com.pulserelay.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterTest {
    @Test
    fun `accepts bKash and redacts phone and long number`() {
        val result = MessageFilter.evaluate(
            IncomingMessage("bKash", "Cash In BDT 500 from 01712345678. Ref 123456789"),
        )

        assertTrue(result.accepted)
        assertEquals(Provider.BKASH, result.provider)
        assertTrue(result.safeBody!!.contains("[phone redacted]"))
        assertTrue(result.safeBody!!.contains("[number redacted]"))
    }

    @Test
    fun `rejects unknown sender`() {
        val result = MessageFilter.evaluate(IncomingMessage("Unknown", "Cash In BDT 500"))
        assertFalse(result.accepted)
    }

    @Test
    fun `rejects otp content even for approved provider`() {
        val result = MessageFilter.evaluate(IncomingMessage("Nagad", "Your OTP is 123456"))
        assertFalse(result.accepted)
        assertEquals("Possible OTP or PIN content", result.reason)
    }
}
