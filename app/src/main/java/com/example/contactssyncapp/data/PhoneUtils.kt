package com.example.contactssyncapp.data

object PhoneUtils {

    /**
     * Extracts the primary clean phone number for dialing and clipboard copying.
     * Handles multiple numbers separated by slash, comma, or newlines (e.g. "0912640367 / 0921234567")
     * and strips all unwanted characters except digits and '+'.
     */
    fun extractCleanPhone(rawPhone: String?): String {
        if (rawPhone.isNullOrBlank()) return ""
        // Take the first segment if multiple numbers are present
        val firstSegment = rawPhone.split(Regex("[/,;\\n]")).firstOrNull()?.trim() ?: rawPhone.trim()
        val cleaned = firstSegment.replace(Regex("[^0-9+]"), "")
        val digitCount = cleaned.count { it.isDigit() }
        return if (digitCount >= 4) cleaned else ""
    }

    /**
     * Formats phone number for clean, high-contrast display (e.g. 091-264-0367).
     */
    fun formatPhoneForDisplay(rawPhone: String?): String {
        val clean = extractCleanPhone(rawPhone)
        if (clean.isBlank()) return ""
        return if (clean.length == 10 && clean.startsWith("0")) {
            "${clean.substring(0, 3)}-${clean.substring(3, 6)}-${clean.substring(6)}"
        } else if (clean.length == 12 && clean.startsWith("218")) {
            "+${clean.substring(0, 3)} ${clean.substring(3, 5)} ${clean.substring(5, 8)} ${clean.substring(8)}"
        } else if (clean.length == 13 && clean.startsWith("+218")) {
            "${clean.substring(0, 4)} ${clean.substring(4, 6)} ${clean.substring(6, 9)} ${clean.substring(9)}"
        } else {
            clean
        }
    }
}
