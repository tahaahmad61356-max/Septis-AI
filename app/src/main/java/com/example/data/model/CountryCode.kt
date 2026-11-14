package com.example.data.model

data class CountryCode(
    val iso: String,
    val name: String,
    val dialCode: String,
    val flag: String,
    val example: String
)

object CountryCodes {
    val all: List<CountryCode> = listOf(
        CountryCode("PK", "Pakistan", "+92", "\uD83C\uDDF5\uD83C\uDDF0", "3001234567"),
        CountryCode("US", "United States", "+1", "\uD83C\uDDFA\uD83C\uDDF8", "5551234567"),
        CountryCode("GB", "United Kingdom", "+44", "\uD83C\uDDEC\uD83C\uDDE7", "7700900123"),
        CountryCode("IN", "India", "+91", "\uD83C\uDDEE\uD83C\uDDF3", "9876543210"),
        CountryCode("AE", "United Arab Emirates", "+971", "\uD83C\uDDE6\uD83C\uDDEA", "501234567"),
        CountryCode("SA", "Saudi Arabia", "+966", "\uD83C\uDDF8\uD83C\uDDE6", "501234567"),
        CountryCode("CA", "Canada", "+1", "\uD83C\uDDE8\uD83C\uDDE6", "4165550123"),
        CountryCode("AU", "Australia", "+61", "\uD83C\uDDE6\uD83C\uDDFA", "412345678"),
        CountryCode("DE", "Germany", "+49", "\uD83C\uDDE9\uD83C\uDDEA", "15123456789"),
        CountryCode("FR", "France", "+33", "\uD83C\uDDEB\uD83C\uDDF7", "612345678"),
        CountryCode("TR", "Turkey", "+90", "\uD83C\uDDF9\uD83C\uDDF7", "5321234567"),
        CountryCode("BD", "Bangladesh", "+880", "\uD83C\uDDE7\uD83C\uDDE9", "1712345678"),
        CountryCode("ID", "Indonesia", "+62", "\uD83C\uDDEE\uD83C\uDDE9", "8123456789"),
        CountryCode("MY", "Malaysia", "+60", "\uD83C\uDDF2\uD83C\uDDFE", "123456789"),
        CountryCode("NG", "Nigeria", "+234", "\uD83C\uDDF3\uD83C\uDDEC", "8012345678"),
        CountryCode("EG", "Egypt", "+20", "\uD83C\uDDEA\uD83C\uDDEC", "1001234567"),
        CountryCode("ZA", "South Africa", "+27", "\uD83C\uDDFF\uD83C\uDDE6", "711234567"),
        CountryCode("BR", "Brazil", "+55", "\uD83C\uDDE7\uD83C\uDDF7", "11912345678"),
        CountryCode("MX", "Mexico", "+52", "\uD83C\uDDF2\uD83C\uDDFD", "5512345678"),
        CountryCode("JP", "Japan", "+81", "\uD83C\uDDEF\uD83C\uDDF5", "9012345678"),
        CountryCode("KR", "South Korea", "+82", "\uD83C\uDDF0\uD83C\uDDF7", "1012345678"),
        CountryCode("SG", "Singapore", "+65", "\uD83C\uDDF8\uD83C\uDDEC", "81234567"),
        CountryCode("PH", "Philippines", "+63", "\uD83C\uDDF5\uD83C\uDDED", "9171234567"),
        CountryCode("ES", "Spain", "+34", "\uD83C\uDDEA\uD83C\uDDF8", "612345678"),
        CountryCode("IT", "Italy", "+39", "\uD83C\uDDEE\uD83C\uDDF9", "3123456789"),
        CountryCode("NL", "Netherlands", "+31", "\uD83C\uDDF3\uD83C\uDDF1", "612345678"),
        CountryCode("QA", "Qatar", "+974", "\uD83C\uDDF6\uD83C\uDDE6", "33123456"),
        CountryCode("KW", "Kuwait", "+965", "\uD83C\uDDF0\uD83C\uDDFC", "50012345"),
        CountryCode("OM", "Oman", "+968", "\uD83C\uDDF4\uD83C\uDDF2", "92123456"),
        CountryCode("BH", "Bahrain", "+973", "\uD83C\uDDE7\uD83C\uDDED", "36001234"),
        CountryCode("AF", "Afghanistan", "+93", "\uD83C\uDDE6\uD83C\uDDEB", "701234567"),
        CountryCode("IR", "Iran", "+98", "\uD83C\uDDEE\uD83C\uDDF7", "9123456789"),
        CountryCode("CN", "China", "+86", "\uD83C\uDDE8\uD83C\uDDF3", "13800138000"),
        CountryCode("RU", "Russia", "+7", "\uD83C\uDDF7\uD83C\uDDFA", "9123456789"),
        CountryCode("SE", "Sweden", "+46", "\uD83C\uDDF8\uD83C\uDDEA", "701234567"),
        CountryCode("NO", "Norway", "+47", "\uD83C\uDDF3\uD83C\uDDF4", "41234567"),
        CountryCode("CH", "Switzerland", "+41", "\uD83C\uDDE8\uD83C\uDDED", "781234567"),
        CountryCode("KE", "Kenya", "+254", "\uD83C\uDDF0\uD83C\uDDEA", "712345678"),
        CountryCode("NZ", "New Zealand", "+64", "\uD83C\uDDF3\uD83C\uDDFF", "211234567")
    )

    val defaultCountry: CountryCode = all.first()

    fun findByDialCode(dialCode: String): CountryCode? =
        all.firstOrNull { it.dialCode == dialCode }
}

object PhoneUtils {
    /**
     * Normalizes a user-entered or device-contact phone number to E.164 format (e.g., "+923001234567").
     * Returns null if fewer than 6 digits remain after normalization.
     */
    fun normalizeToE164(rawInput: String, defaultCountryCode: String = "+92"): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length < 5) return null

        val e164 = when {
            hasPlus -> "+$digitsOnly"
            trimmed.startsWith("00") && digitsOnly.length > 7 -> "+${digitsOnly.drop(2)}"
            else -> {
                val ccDigits = defaultCountryCode.filter { it.isDigit() }
                val national = digitsOnly.trimStart('0')
                if (national.isEmpty()) return null
                "+$ccDigits$national"
            }
        }
        val finalDigits = e164.drop(1)
        return if (finalDigits.length in 6..15) e164 else null
    }

    /**
     * Builds an E.164 string directly from an explicit country dial code and national phone input.
     */
    fun combineToE164(dialCode: String, nationalInput: String): String? {
        val rawTrimmed = nationalInput.trim()
        if (rawTrimmed.startsWith("+")) {
            return normalizeToE164(rawTrimmed, dialCode)
        }
        val ccDigits = dialCode.filter { it.isDigit() }
        val natDigits = rawTrimmed.filter { it.isDigit() }.trimStart('0')
        if (ccDigits.isEmpty() || natDigits.length < 4) return null
        val combined = "+$ccDigits$natDigits"
        val total = combined.drop(1).length
        return if (total in 6..15) combined else null
    }

    /**
     * Deterministic Firestore document key matching ^[a-zA-Z0-9_\-\.]+$ for E.164 lookup.
     */
    fun toDirectoryKey(e164: String): String {
        val digits = e164.filter { it.isDigit() }
        return "p_$digits"
    }

    fun extractNationalNumber(e164: String, dialCode: String): String {
        val cc = dialCode.filter { it.isDigit() }
        val all = e164.filter { it.isDigit() }
        return if (all.startsWith(cc) && all.length > cc.length) {
            all.removePrefix(cc)
        } else {
            all
        }
    }

    fun detectCountry(e164: String): CountryCode {
        val sortedByDialLen = CountryCodes.all.sortedByDescending { it.dialCode.length }
        return sortedByDialLen.firstOrNull { e164.startsWith(it.dialCode) } ?: CountryCodes.defaultCountry
    }

    fun generateDirectConversationId(uidA: String, uidB: String): String {
        val (first, second) = if (uidA <= uidB) uidA to uidB else uidB to uidA
        return "dm_${first}_${second}".take(128)
    }
}
