package com.pulserelay.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionIdExtractorTest {
    @Test
    fun `extracts bKash style trx id`() {
        assertEquals("9HK8Q7XZ9A", TransactionIdExtractor.extract("Cash In BDT 500. TrxID 9HK8Q7XZ9A"))
    }

    @Test
    fun `extracts colon separated trx id`() {
        assertEquals("9F3DXQ7K", TransactionIdExtractor.extract("TrxID: 9F3DXQ7K"))
    }

    @Test
    fun `extracts transaction id spelling`() {
        assertEquals("2025ABC123", TransactionIdExtractor.extract("Transaction ID: 2025ABC123"))
    }

    @Test
    fun `extracts pure numeric transaction id`() {
        assertEquals("123456789012", TransactionIdExtractor.extract("TrxID 123456789012"))
    }

    @Test
    fun `ignores phone numbers as transaction ids`() {
        assertNull(TransactionIdExtractor.extract("TrxID 01712345678"))
    }

    @Test
    fun `returns null when no label is present`() {
        assertNull(TransactionIdExtractor.extract("Cash In BDT 500 Ref 123456"))
    }
}
