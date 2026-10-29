package com.safeher.app.data.demo

import android.content.Context
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Self-contained mock AuthRepository implementation for SafeHer Demo Mode.
 *
 * Operates entirely on-device without contacting Firebase Authentication or any remote server.
 * Evaluators can switch between Demo User and Demo Guardian profiles to observe both ends
 * of the safety alerting workflow.
 */
class DemoAuthRepository(
    private val demoModeManager: DemoModeManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var explicitSignOut: Boolean = false

    init {
        _authState.value = AuthState.Authenticated(DemoProfile.USER.toAuthUser())

        demoModeManager.registerResetListener {
            explicitSignOut = false
            _authState.value = AuthState.Authenticated(DemoProfile.USER.toAuthUser())
        }
    }

    override fun getCurrentUser(): AuthUser? {
        return if (explicitSignOut) null else DemoProfile.USER.toAuthUser()
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = withContext(dispatcher) {
        if (!email.contains("@") || email.length < 5) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters.")
        }
        explicitSignOut = false
        val user = demoModeManager.activeProfile.value.toAuthUser().copy(email = email.trim())
        _authState.value = AuthState.Authenticated(user)
        AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): AuthResult = withContext(dispatcher) {
        if (!email.contains("@") || email.length < 5) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters.")
        }
        explicitSignOut = false
        val user = AuthUser(
            uid = "demo_custom_user_${System.currentTimeMillis() % 10000}",
            email = email.trim(),
            displayName = displayName?.ifBlank { "Demo User" } ?: "Demo User",
            isEmailVerified = false
        )
        _authState.value = AuthState.Authenticated(user)
        AuthResult.Success(user)
    }

    override suspend fun signInWithGoogle(context: Context, webClientId: String): AuthResult = withContext(dispatcher) {
        explicitSignOut = false
        val user = demoModeManager.activeProfile.value.toAuthUser()
        _authState.value = AuthState.Authenticated(user)
        AuthResult.Success(user)
    }

    override suspend fun sendEmailVerification(): Result<Unit> = withContext(dispatcher) {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            _authState.value = AuthState.Authenticated(current.user.copy(isEmailVerified = true))
        }
        Result.success(Unit)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(dispatcher) {
        if (!email.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Please provide a valid email."))
        }
        Result.success(Unit)
    }

    override suspend fun signOut(): Unit = withContext(dispatcher) {
        explicitSignOut = true
        _authState.value = AuthState.Unauthenticated
    }
}
