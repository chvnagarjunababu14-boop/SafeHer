package com.safeher.app.ui.screens.welcome

/**
 * Screen destinations within the first-launch welcome and onboarding navigation graph.
 */
enum class WelcomeStep {
    /**
     * SafeHer primary landing screen with headline, brand identity, and entry buttons.
     */
    WELCOME,

    /**
     * 4-slide swipeable introduction detailing SafeHer safety principles.
     */
    ONBOARDING,

    /**
     * Legacy/generic direct sign-in, registration, and Google authentication screen.
     */
    AUTH,

    /**
     * Personal Safety User login screen.
     */
    USER_LOGIN,

    /**
     * Personal Safety User registration screen.
     */
    USER_SIGN_UP,

    /**
     * Guardian login screen.
     */
    GUARDIAN_LOGIN,

    /**
     * Guardian registration screen.
     */
    GUARDIAN_SIGN_UP,

    /**
     * Guided post-registration setup checklist (guardian, location, SMS consent, notifications).
     */
    FIRST_LOGIN_SETUP,

    /**
     * Dedicated Guardian connection-code screen to connect with a SafeHer user.
     */
    GUARDIAN_CONNECT
}
