package com.safeher.app.data.remoteguardian

data class RemoteGuardian(
    val relationshipId: String,
    val guardianUid: String,
    val guardianDisplayName: String,
    val guardianEmail: String,
    val status: String, // "ACTIVE" | "REVOKED"
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true)
}

data class GuardianInvite(
    val inviteId: String,
    val code: String,
    val expiresAtMillis: Long
)
