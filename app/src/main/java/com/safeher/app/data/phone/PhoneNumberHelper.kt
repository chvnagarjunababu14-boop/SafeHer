package com.safeher.app.data.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber

data class CountryInfo(
    val countryCode: String,
    val dialingCode: String,
    val name: String,
    val flagEmoji: String,
    val placeholder: String
)

sealed interface PhoneParseResult {
    data class Valid(
        val e164: String,
        val international: String,
        val national: String,
        val country: CountryInfo,
        val wasAutoDetected: Boolean = false
    ) : PhoneParseResult

    data class Invalid(
        val reason: String
    ) : PhoneParseResult
}

object CountryRegistry {
    val DEFAULT_COUNTRY = CountryInfo(
        countryCode = "IN",
        dialingCode = "+91",
        name = "India",
        flagEmoji = "🇮🇳",
        placeholder = "98765 43210"
    )

    val COUNTRIES: List<CountryInfo> = listOf(
        DEFAULT_COUNTRY,
        CountryInfo("US", "+1", "United States", "🇺🇸", "555 123 4567"),
        CountryInfo("GB", "+44", "United Kingdom", "🇬🇧", "7911 123456"),
        CountryInfo("CA", "+1", "Canada", "🇨🇦", "555 123 4567"),
        CountryInfo("AU", "+61", "Australia", "🇦🇺", "412 345 678"),
        CountryInfo("AE", "+971", "United Arab Emirates", "🇦🇪", "50 123 4567"),
        CountryInfo("SG", "+65", "Singapore", "🇸🇬", "8123 4567"),
        CountryInfo("DE", "+49", "Germany", "🇩🇪", "151 23456789"),
        CountryInfo("FR", "+33", "France", "🇫🇷", "6 12 34 56 78"),
        CountryInfo("JP", "+81", "Japan", "🇯🇵", "90 1234 5678"),
        CountryInfo("NZ", "+64", "New Zealand", "🇳🇿", "21 123 4567"),
        CountryInfo("ZA", "+27", "South Africa", "🇿🇦", "71 123 4567"),
        CountryInfo("MY", "+60", "Malaysia", "🇲🇾", "12 345 6789"),
        CountryInfo("PH", "+63", "Philippines", "🇵🇭", "917 123 4567"),
        CountryInfo("SA", "+966", "Saudi Arabia", "🇸🇦", "50 123 4567"),
        CountryInfo("BR", "+55", "Brazil", "🇧🇷", "11 91234 5678"),
        CountryInfo("MX", "+52", "Mexico", "🇲🇽", "55 1234 5678"),
        CountryInfo("NG", "+234", "Nigeria", "🇳🇬", "802 123 4567"),
        CountryInfo("KE", "+254", "Kenya", "🇰🇪", "712 345678"),
        CountryInfo("PK", "+92", "Pakistan", "🇵🇰", "301 2345678"),
        CountryInfo("BD", "+880", "Bangladesh", "🇧🇩", "1712 345678"),
        CountryInfo("NP", "+977", "Nepal", "🇳🇵", "984 1234567"),
        CountryInfo("LK", "+94", "Sri Lanka", "🇱🇰", "71 234 5678"),
        CountryInfo("ES", "+34", "Spain", "🇪🇸", "612 34 56 78"),
        CountryInfo("IT", "+39", "Italy", "🇮🇹", "312 345 6789"),
        CountryInfo("IE", "+353", "Ireland", "🇮🇪", "85 123 4567"),
        CountryInfo("NL", "+31", "Netherlands", "🇳🇱", "6 12345678"),
        CountryInfo("CH", "+41", "Switzerland", "🇨🇭", "78 123 45 67"),
        CountryInfo("SE", "+46", "Sweden", "🇸🇪", "70 123 45 67"),
        CountryInfo("NO", "+47", "Norway", "🇳🇴", "412 34 567"),
        CountryInfo("DK", "+45", "Denmark", "🇩🇰", "20 12 34 56"),
        CountryInfo("ID", "+62", "Indonesia", "🇮🇩", "812 3456 7890"),
        CountryInfo("KR", "+82", "South Korea", "🇰🇷", "10 1234 5678"),
        CountryInfo("CN", "+86", "China", "🇨🇳", "138 0013 8000")
    )

    fun findByCountryCode(code: String?): CountryInfo {
        if (code == null) return DEFAULT_COUNTRY
        return COUNTRIES.firstOrNull { it.countryCode.equals(code, ignoreCase = true) }
            ?: DEFAULT_COUNTRY
    }

    fun findByDialingCode(dialingCode: String): CountryInfo {
        val clean = if (dialingCode.startsWith("+")) dialingCode else "+$dialingCode"
        return COUNTRIES.firstOrNull { it.dialingCode == clean } ?: DEFAULT_COUNTRY
    }

    fun search(query: String): List<CountryInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return COUNTRIES
        return COUNTRIES.filter {
            it.name.lowercase().contains(q) ||
            it.dialingCode.contains(q) ||
            it.countryCode.lowercase().contains(q)
        }
    }
}

object PhoneNumberHelper {
    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * Parses and normalizes user input into an E.164 canonical phone number.
     * Prevents double-prefixing and auto-detects international pasted numbers.
     */
    fun parseAndValidate(
        rawInput: String,
        selectedCountry: CountryInfo
    ): PhoneParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return PhoneParseResult.Invalid("Phone number is required")
        }

        // 1. If input explicitly starts with +, parse as international
        if (trimmed.startsWith("+")) {
            return parseInternational(trimmed, selectedCountry)
        }

        // 2. Check if the user pasted a number that starts with the selected country's numeric dialing code (e.g. "919876543210")
        val numericDialingCode = selectedCountry.dialingCode.removePrefix("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        
        if (digitsOnly.startsWith(numericDialingCode) && digitsOnly.length > numericDialingCode.length + 6) {
            val withPlus = "+$digitsOnly"
            try {
                val parsed = phoneUtil.parse(withPlus, null)
                val isPossible = phoneUtil.isPossibleNumberWithReason(parsed) == PhoneNumberUtil.ValidationResult.IS_POSSIBLE
                if (phoneUtil.isValidNumber(parsed) || isPossible) {
                    val country = resolveCountry(parsed)
                    val nationalNumber = phoneUtil.getNationalSignificantNumber(parsed)
                    return PhoneParseResult.Valid(
                        e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                        international = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
                        national = nationalNumber,
                        country = country,
                        wasAutoDetected = true
                    )
                }
            } catch (_: Exception) {
                // Fall through to standard national parsing
            }
        }

        // 3. Check if user pasted an international number starting with 00 (common international exit prefix)
        if (trimmed.startsWith("00")) {
            val asPlus = "+" + trimmed.removePrefix("00")
            return parseInternational(asPlus, selectedCountry)
        }

        // 4. Standard national parsing using selected country
        return try {
            val parsed = phoneUtil.parse(trimmed, selectedCountry.countryCode)
            val isPossible = phoneUtil.isPossibleNumberWithReason(parsed) == PhoneNumberUtil.ValidationResult.IS_POSSIBLE
            if (phoneUtil.isValidNumber(parsed) || isPossible) {
                val nationalNumber = phoneUtil.getNationalSignificantNumber(parsed)
                PhoneParseResult.Valid(
                    e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                    international = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
                    national = nationalNumber,
                    country = selectedCountry,
                    wasAutoDetected = false
                )
            } else {
                val reason = phoneUtil.isPossibleNumberWithReason(parsed)
                val msg = when (reason) {
                    PhoneNumberUtil.ValidationResult.TOO_SHORT ->
                        "Phone number is too short for ${selectedCountry.name} (${selectedCountry.dialingCode})"
                    PhoneNumberUtil.ValidationResult.TOO_LONG ->
                        "Phone number is too long for ${selectedCountry.name} (${selectedCountry.dialingCode})"
                    PhoneNumberUtil.ValidationResult.INVALID_LENGTH ->
                        "Invalid length for ${selectedCountry.name} phone number"
                    else ->
                        "Invalid phone number for ${selectedCountry.name} (${selectedCountry.dialingCode})"
                }
                PhoneParseResult.Invalid(msg)
            }
        } catch (e: NumberParseException) {
            PhoneParseResult.Invalid(
                when (e.errorType) {
                    NumberParseException.ErrorType.TOO_SHORT_NSN ->
                        "Phone number is too short for ${selectedCountry.name}"
                    NumberParseException.ErrorType.TOO_LONG ->
                        "Phone number is too long for ${selectedCountry.name}"
                    else -> "Invalid phone number: ${e.message}"
                }
            )
        }
    }

    private fun parseInternational(
        internationalInput: String,
        selectedCountry: CountryInfo
    ): PhoneParseResult {
        return try {
            val parsed = phoneUtil.parse(internationalInput, null)
            val isPossible = phoneUtil.isPossibleNumberWithReason(parsed) == PhoneNumberUtil.ValidationResult.IS_POSSIBLE
            if (phoneUtil.isValidNumber(parsed) || isPossible) {
                val country = resolveCountry(parsed)
                val nationalNumber = phoneUtil.getNationalSignificantNumber(parsed)
                val wasAutoDetected = country.countryCode != selectedCountry.countryCode
                PhoneParseResult.Valid(
                    e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                    international = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
                    national = nationalNumber,
                    country = country,
                    wasAutoDetected = wasAutoDetected
                )
            } else {
                PhoneParseResult.Invalid("Invalid international phone number")
            }
        } catch (e: NumberParseException) {
            PhoneParseResult.Invalid("Could not parse international number: ${e.message}")
        }
    }

    private fun resolveCountry(parsed: PhoneNumber): CountryInfo {
        val region = phoneUtil.getRegionCodeForNumber(parsed)
        if (!region.isNullOrBlank() && region != "ZZ") {
            val found = CountryRegistry.findByCountryCode(region)
            if (found.countryCode.equals(region, ignoreCase = true)) {
                return found
            }
        }
        val fallbackRegion = phoneUtil.getRegionCodeForCountryCode(parsed.countryCode)
        if (!fallbackRegion.isNullOrBlank() && fallbackRegion != "ZZ") {
            val found = CountryRegistry.findByCountryCode(fallbackRegion)
            if (found.countryCode.equals(fallbackRegion, ignoreCase = true)) {
                return found
            }
        }
        return CountryRegistry.findByDialingCode("+${parsed.countryCode}")
    }

    /**
     * Extracts national number and country from an existing stored phone number (typically E.164).
     */
    fun extractDetails(raw: String, fallbackCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY): Pair<CountryInfo, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Pair(fallbackCountry, "")

        val toParse = if (trimmed.startsWith("+")) trimmed else "+$trimmed"
        return try {
            val parsed = phoneUtil.parse(toParse, null)
            val country = resolveCountry(parsed)
            val national = phoneUtil.getNationalSignificantNumber(parsed)
            Pair(country, national)
        } catch (_: Exception) {
            Pair(fallbackCountry, trimmed.filter { it.isDigit() })
        }
    }

    /**
     * Formats a stored number for readable display on guardian cards.
     * e.g. "+919876543210" -> "+91 98765 43210"
     */
    fun formatForDisplay(storedNumber: String): String {
        val trimmed = storedNumber.trim()
        if (trimmed.isEmpty()) return ""
        val toParse = if (trimmed.startsWith("+")) trimmed else "+$trimmed"
        return try {
            val parsed = phoneUtil.parse(toParse, null)
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            } else {
                storedNumber
            }
        } catch (_: Exception) {
            storedNumber
        }
    }

    /**
     * Canonical normalization function for duplicate detection.
     */
    fun toCanonicalE164(raw: String, defaultCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY): String? {
        val result = parseAndValidate(raw, defaultCountry)
        return when (result) {
            is PhoneParseResult.Valid -> result.e164
            is PhoneParseResult.Invalid -> null
        }
    }
}
