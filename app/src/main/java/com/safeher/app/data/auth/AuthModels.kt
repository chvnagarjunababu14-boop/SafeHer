package com.safeher.app.data.auth

data class AuthUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
    val isAnonymous: Boolean = false
) {
    val displayLabel: String
        get() = when {
            !displayName.isNullOrBlank() -> displayName
            !email.isNullOrBlank() -> email.substringBefore("@")
            else -> "SafeHer User"
        }
}

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
    data object Loading : AuthState
}

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Error(val message: String, val code: String? = null) : AuthResult
    data object Cancelled : AuthResult
}
