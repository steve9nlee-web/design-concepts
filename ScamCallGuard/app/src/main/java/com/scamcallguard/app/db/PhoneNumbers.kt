package com.scamcallguard.app.db

object PhoneNumbers {

    /**
     * Normalizes a phone number to a digits-only form for matching.
     * Keeps a leading "+" indicator by stripping it after digit extraction,
     * and matches on the trailing 10 digits when longer, so that
     * "+1 (555) 123-4567", "15551234567" and "555-123-4567" all collide.
     * Returns null when the input contains fewer than 3 digits.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 3) return null
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /** Formats a normalized number for display. */
    fun display(normalized: String): String =
        if (normalized.length == 10) {
            "(${normalized.substring(0, 3)}) ${normalized.substring(3, 6)}-${normalized.substring(6)}"
        } else {
            normalized
        }
}
