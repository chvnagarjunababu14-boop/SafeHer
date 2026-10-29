package com.safeher.app.data.remoteguardian

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Generates and validates human-readable invitation phrases.
 *
 * Format example: `BLUE-TIGER-42-MAPLE`
 * Document lookup key is SHA-256 hex of the normalized phrase (not a sequential ID).
 */
object InvitePhraseGenerator {

    const val INVITE_TTL_MS: Long = 48L * 60L * 60L * 1000L
    private const val MAX_CREATE_ATTEMPTS = 8

    private val phraseRegex = Regex("^[A-Z]+-[A-Z]+-\\d{2}-[A-Z]+$")
    private val secureRandom = SecureRandom()

    fun normalize(raw: String): String {
        return raw.trim()
            .uppercase(Locale.US)
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
    }

    fun isValidFormat(normalized: String): Boolean {
        if (!phraseRegex.matches(normalized)) return false
        val parts = normalized.split('-')
        if (parts.size != 4) return false
        val number = parts[2].toIntOrNull() ?: return false
        return number in 10..99
    }

    fun documentIdForPhrase(normalizedPhrase: String): String {
        require(isValidFormat(normalizedPhrase)) { "Invalid invitation phrase format." }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalizedPhrase.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { b -> "%02x".format(b) }
    }

    fun generatePhrase(): String {
        val words = InvitePhraseWords.WORDS
        require(words.size >= 100) { "Invitation word list is too small." }
        val w1 = words[secureRandom.nextInt(words.size)].uppercase(Locale.US)
        val w2 = words[secureRandom.nextInt(words.size)].uppercase(Locale.US)
        var w3 = words[secureRandom.nextInt(words.size)].uppercase(Locale.US)
        // Prefer distinct trailing word when possible
        var guard = 0
        while ((w3 == w1 || w3 == w2) && guard < 8) {
            w3 = words[secureRandom.nextInt(words.size)].uppercase(Locale.US)
            guard++
        }
        val number = 10 + secureRandom.nextInt(90) // 10..99
        return "$w1-$w2-$number-$w3"
    }

    /**
     * Generates a phrase whose document ID does not collide with [exists].
     */
    fun generateUniquePhrase(exists: (documentId: String) -> Boolean): String {
        repeat(MAX_CREATE_ATTEMPTS) {
            val phrase = generatePhrase()
            val id = documentIdForPhrase(phrase)
            if (!exists(id)) return phrase
        }
        throw IllegalStateException("Could not allocate a unique invitation phrase. Please try again.")
    }

    fun expiresAtMillis(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis + INVITE_TTL_MS
}
