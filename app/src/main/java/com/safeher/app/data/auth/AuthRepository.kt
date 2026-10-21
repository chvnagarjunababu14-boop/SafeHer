package com.safeher.app.data.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    fun getCurrentUser(): AuthUser?

    suspend fun signInWithEmail(email: String, password: String): AuthResult

    suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun sendEmailVerification(): Result<Unit>

    suspend fun signInWithGoogle(context: Context, webClientId: String): AuthResult

    suspend fun signOut()
}
