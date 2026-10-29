package com.safeher.app

import com.safeher.app.data.remoteguardian.InvitePhraseGenerator
import com.safeher.app.data.remoteguardian.InvitePhraseWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitePhraseGeneratorTest {

    @Test
    fun wordList_hasSufficientEntropyPool() {
        assertTrue(InvitePhraseWords.WORDS.size >= 1000)
        assertEquals(InvitePhraseWords.WORDS.size, InvitePhraseWords.WORDS.toSet().size)
    }

    @Test
    fun generatePhrase_matchesExpectedFormat() {
        repeat(40) {
            val phrase = InvitePhraseGenerator.generatePhrase()
            assertTrue("Unexpected phrase: $phrase", InvitePhraseGenerator.isValidFormat(phrase))
            val parts = phrase.split('-')
            assertEquals(4, parts.size)
            assertTrue(parts[2].toInt() in 10..99)
        }
    }

    @Test
    fun generatePhrase_isNotDeterministicSequence() {
        val samples = (1..30).map { InvitePhraseGenerator.generatePhrase() }.toSet()
        assertTrue("Expected diverse phrases, got ${samples.size}", samples.size >= 20)
    }

    @Test
    fun normalize_uppercasesAndCollapsesWhitespace() {
        assertEquals(
            "BLUE-TIGER-42-MAPLE",
            InvitePhraseGenerator.normalize("  blue tiger 42 maple ")
        )
        assertEquals(
            "BLUE-TIGER-42-MAPLE",
            InvitePhraseGenerator.normalize("blue-tiger-42-maple")
        )
    }

    @Test
    fun isValidFormat_rejectsMalformedPhrases() {
        assertFalse(InvitePhraseGenerator.isValidFormat(""))
        assertFalse(InvitePhraseGenerator.isValidFormat("ABCDEF12"))
        assertFalse(InvitePhraseGenerator.isValidFormat("BLUE-TIGER-4-MAPLE"))
        assertFalse(InvitePhraseGenerator.isValidFormat("BLUE-TIGER-42"))
        assertFalse(InvitePhraseGenerator.isValidFormat("blue-tiger-42-maple"))
        assertTrue(InvitePhraseGenerator.isValidFormat("BLUE-TIGER-42-MAPLE"))
    }

    @Test
    fun documentIdForPhrase_isStableSha256Hex() {
        val phrase = "BLUE-TIGER-42-MAPLE"
        val id1 = InvitePhraseGenerator.documentIdForPhrase(phrase)
        val id2 = InvitePhraseGenerator.documentIdForPhrase(phrase)
        assertEquals(64, id1.length)
        assertEquals(id1, id2)
        assertTrue(id1.matches(Regex("^[0-9a-f]{64}$")))
        assertNotEquals(
            id1,
            InvitePhraseGenerator.documentIdForPhrase("BLUE-TIGER-43-MAPLE")
        )
    }

    @Test
    fun expiresAtMillis_isFortyEightHoursAhead() {
        val now = 1_700_000_000_000L
        assertEquals(now + InvitePhraseGenerator.INVITE_TTL_MS, InvitePhraseGenerator.expiresAtMillis(now))
        assertEquals(48L * 60L * 60L * 1000L, InvitePhraseGenerator.INVITE_TTL_MS)
    }

    @Test
    fun generateUniquePhrase_retriesOnCollision() {
        val first = InvitePhraseGenerator.generatePhrase()
        val firstId = InvitePhraseGenerator.documentIdForPhrase(first)
        var calls = 0
        val unique = InvitePhraseGenerator.generateUniquePhrase { id ->
            calls++
            id == firstId && calls == 1
        }
        assertTrue(InvitePhraseGenerator.isValidFormat(unique))
        assertNotEquals(firstId, InvitePhraseGenerator.documentIdForPhrase(unique))
    }
}
