package com.pulserelay.app.domain

/**
 * Pure registry logic for duplicate transaction IDs. It is persisted by the data layer;
 * this object only decides whether an ID is new or a repeat, and keeps the registry bounded.
 */
object DuplicateDetector {
    const val MAX_TRACKED_IDS = 1000

    /**
     * Returns `(isDuplicate, updatedRegistry)`.
     * The returned registry is the original one when the ID is a duplicate.
     */
    fun register(id: String, seen: Set<String>): Pair<Boolean, Set<String>> {
        val normalized = id.trim().uppercase()
        if (normalized.isBlank()) return false to seen
        if (normalized in seen) return true to seen

        val updated = seen + normalized
        val capped = if (updated.size > MAX_TRACKED_IDS) {
            updated.drop(updated.size - MAX_TRACKED_IDS).toSet()
        } else {
            updated
        }
        return false to capped
    }
}
