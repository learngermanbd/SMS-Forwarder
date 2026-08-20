package com.pulserelay.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    @Test
    fun `first registration is not a duplicate`() {
        val (isDuplicate, updated) = DuplicateDetector.register("9HK8Q7XZ9A", emptySet())
        assertFalse(isDuplicate)
        assertTrue("9HK8Q7XZ9A" in updated)
    }

    @Test
    fun `second registration is a duplicate`() {
        val (_, updated) = DuplicateDetector.register("9HK8Q7XZ9A", emptySet())
        val (isDuplicate, registry) = DuplicateDetector.register("9HK8Q7XZ9A", updated)
        assertTrue(isDuplicate)
        assertTrue(registry === updated)
    }

    @Test
    fun `normalizes case and surrounding whitespace`() {
        val (first, updated) = DuplicateDetector.register("  9hk8q7xz9a  ", emptySet())
        assertFalse(first)
        val (second, _) = DuplicateDetector.register("9HK8Q7XZ9A", updated)
        assertTrue(second)
    }
}
