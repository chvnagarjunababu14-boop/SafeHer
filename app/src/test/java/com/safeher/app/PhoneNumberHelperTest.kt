package com.safeher.app

import com.safeher.app.data.phone.CountryInfo
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneParseResult
import com.safeher.app.data.phone.PhoneNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberHelperTest {

    @Test
    fun indiaLocalNumber_normalizesToE164() {
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "9876543210",
            selectedCountry = CountryRegistry.DEFAULT_COUNTRY
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+919876543210", valid.e164)
        assertEquals("9876543210", valid.national)
        assertEquals("IN", valid.country.countryCode)
        assertFalse(valid.wasAutoDetected)
    }

    @Test
    fun indiaLeadingZeroNumber_normalizesToE164() {
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "09876543210",
            selectedCountry = CountryRegistry.DEFAULT_COUNTRY
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+919876543210", valid.e164)
    }

    @Test
    fun usaNumber_normalizesToE164() {
        val usCountry = CountryRegistry.findByCountryCode("US")
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "2025550123",
            selectedCountry = usCountry
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+12025550123", valid.e164)
        assertEquals("US", valid.country.countryCode)
    }

    @Test
    fun ukNumber_normalizesToE164() {
        val ukCountry = CountryRegistry.findByCountryCode("GB")
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "07911 123456",
            selectedCountry = ukCountry
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+447911123456", valid.e164)
        assertEquals("GB", valid.country.countryCode)
    }

    @Test
    fun australiaNumber_normalizesToE164() {
        val auCountry = CountryRegistry.findByCountryCode("AU")
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "0412 345 678",
            selectedCountry = auCountry
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+61412345678", valid.e164)
        assertEquals("AU", valid.country.countryCode)
    }

    @Test
    fun pastedInternationalNumber_autoDetectsCountryAndPreventsDoublePrefix() {
        // User has India selected, but pastes a UK number "+44 7911 123456"
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "+44 7911 123456",
            selectedCountry = CountryRegistry.DEFAULT_COUNTRY
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+447911123456", valid.e164)
        assertEquals("GB", valid.country.countryCode)
        assertTrue("Should detect country auto-switch", valid.wasAutoDetected)
    }

    @Test
    fun pastedNumericDialingCode_doesNotDoublePrefix() {
        // User has India selected, but pastes "919876543210" (starts with 91, no plus)
        val result = PhoneNumberHelper.parseAndValidate(
            rawInput = "919876543210",
            selectedCountry = CountryRegistry.DEFAULT_COUNTRY
        )
        assertTrue(result is PhoneParseResult.Valid)
        val valid = result as PhoneParseResult.Valid
        assertEquals("+919876543210", valid.e164)
        assertEquals("9876543210", valid.national)
    }

    @Test
    fun invalidNumber_isRejectedWithReason() {
        val emptyResult = PhoneNumberHelper.parseAndValidate("", CountryRegistry.DEFAULT_COUNTRY)
        assertTrue(emptyResult is PhoneParseResult.Invalid)

        val shortResult = PhoneNumberHelper.parseAndValidate("123", CountryRegistry.DEFAULT_COUNTRY)
        assertTrue(shortResult is PhoneParseResult.Invalid)
        assertTrue((shortResult as PhoneParseResult.Invalid).reason.contains("short", ignoreCase = true))

        val textResult = PhoneNumberHelper.parseAndValidate("invalid-number", CountryRegistry.DEFAULT_COUNTRY)
        assertTrue(textResult is PhoneParseResult.Invalid)
    }

    @Test
    fun duplicateDetection_recognizesEquivalentFormatsAsEqual() {
        val e164A = PhoneNumberHelper.toCanonicalE164("9876543210", CountryRegistry.DEFAULT_COUNTRY)
        val e164B = PhoneNumberHelper.toCanonicalE164("+91 98765 43210", CountryRegistry.DEFAULT_COUNTRY)
        val e164C = PhoneNumberHelper.toCanonicalE164("09876543210", CountryRegistry.DEFAULT_COUNTRY)
        val e164D = PhoneNumberHelper.toCanonicalE164("+919876543210", CountryRegistry.DEFAULT_COUNTRY)

        assertEquals("+919876543210", e164A)
        assertEquals(e164A, e164B)
        assertEquals(e164B, e164C)
        assertEquals(e164C, e164D)
    }

    @Test
    fun formatForDisplay_formatsCanonicalNumber() {
        val formatted = PhoneNumberHelper.formatForDisplay("+919876543210")
        assertEquals("+91 98765 43210", formatted)

        val formattedUs = PhoneNumberHelper.formatForDisplay("+12025550123")
        assertEquals("+1 202-555-0123", formattedUs)
    }

    @Test
    fun extractDetails_separatesCountryAndNationalNumber() {
        val (country, national) = PhoneNumberHelper.extractDetails("+447911123456")
        assertEquals("GB", country.countryCode)
        assertEquals("7911123456", national)

        val (inCountry, inNational) = PhoneNumberHelper.extractDetails("+919876543210")
        assertEquals("IN", inCountry.countryCode)
        assertEquals("9876543210", inNational)
    }

    @Test
    fun countrySearch_filtersByNameOrCode() {
        val indiaResults = CountryRegistry.search("India")
        assertEquals(1, indiaResults.size)
        assertEquals("IN", indiaResults[0].countryCode)

        val codeResults = CountryRegistry.search("+91")
        assertEquals(1, codeResults.size)
        assertEquals("IN", codeResults[0].countryCode)

        val emptyQueryResults = CountryRegistry.search("")
        assertEquals(CountryRegistry.COUNTRIES.size, emptyQueryResults.size)
    }
}
