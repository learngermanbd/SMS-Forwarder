package com.pulserelay.app.domain

/**
 * Pulls a transaction reference out of a bKash, Nagad, or Rocket receipt.
 *
 * Bangladesh mobile-finance receipts label the reference as TrxID, Transaction ID,
 * Txn ID, or Tx ID. Phone numbers must never be mistaken for a transaction ID.
 */
object TransactionIdExtractor {
    private val labelPatterns = listOf(
        Regex("""(?i)\btrx[ .\-:]*id\b[:\- ]*([A-Z0-9]{6,24})"""),
        Regex("""(?i)\btransaction[ .\-:]*id\b[:\- ]*([A-Z0-9]{6,24})"""),
        Regex("""(?i)\btxn[ .\-:]*id\b[:\- ]*([A-Z0-9]{6,24})"""),
        Regex("""(?i)\btx[ .\-:]*id\b[:\- ]*([A-Z0-9]{6,24})"""),
        Regex("""(?i)\breference[ .\-:]*id\b[:\- ]*([A-Z0-9]{6,24})"""),
    )

    private val phonePattern = Regex("""(?:\+?880|0)?1[3-9]\d{8}""")

    fun extract(body: String): String? {
        for (pattern in labelPatterns) {
            val match = pattern.find(body) ?: continue
            val candidate = match.groupValues[1].uppercase()
            if (phonePattern.containsMatchIn(candidate)) continue
            return candidate
        }
        return null
    }
}
