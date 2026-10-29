package com.safeher.app.data.session

/**
 * Declares the application role for an authenticated SafeHer user.
 *
 * - [PERSONAL_SAFETY_USER]: The existing safety application where the user configures
 *   guardians, tracks local safe journeys, schedules check-ins, and triggers emergency SOS.
 * - [GUARDIAN]: A separate experience where an authorized guardian connects to a
 *   SafeHer user via invitation code to monitor shared journeys, receive emergency alerts,
 *   and view consented locations.
 */
enum class UserRole {
    PERSONAL_SAFETY_USER,
    GUARDIAN
}
