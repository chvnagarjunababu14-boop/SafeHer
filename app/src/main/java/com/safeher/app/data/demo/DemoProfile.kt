package com.safeher.app.data.demo

import com.safeher.app.data.auth.AuthUser

/**
 * Clearly labelled fictional demo profiles for evaluator demonstrations.
 * No real user data, real emails, or real credentials are used.
 */
enum class DemoProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val roleTitle: String,
    val roleDescription: String
) {
    USER(
        uid = "demo_user_sender_101",
        displayName = "Demo User",
        email = "demo.user@safeher.local",
        roleTitle = "Sender (Your Perspective)",
        roleDescription = "Dispatches simulated emergency alerts, tests location consent, and manages remote guardians."
    ),
    GUARDIAN(
        uid = "demo_guardian_priya_202",
        displayName = "Demo Guardian (Priya)",
        email = "priya.guardian@safeher.local",
        roleTitle = "Guardian (Recipient Perspective)",
        roleDescription = "Receives simulated alerts, views emergency location map, and simulates guardian acknowledgement."
    );

    fun toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = true,
        isAnonymous = false
    )
}
