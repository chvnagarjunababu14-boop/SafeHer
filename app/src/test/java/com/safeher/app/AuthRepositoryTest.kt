package com.safeher.app

import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthResult
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var currentUser: AuthUser? = null

    override fun getCurrentUser(): AuthUser? = currentUser

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }
        val user = AuthUser(uid = "user_123", email = trimmed, displayName = "Test User")
        currentUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }
        val user = AuthUser(uid = "user_new", email = trimmed, displayName = displayName)
        currentUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return if (email.contains("@")) Result.success(Unit) else Result.failure(IllegalArgumentException("Invalid email"))
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(context: android.content.Context, webClientId: String): AuthResult {
        if (webClientId.isBlank()) {
            return AuthResult.Error("Web client ID required")
        }
        val user = AuthUser(uid = "google_user", email = "google@test.com", displayName = "Google User")
        currentUser = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        currentUser = null
        _authState.value = AuthState.Unauthenticated
    }
}

class AuthRepositoryTest {

    @Test
    fun initialAuthState_isUnauthenticated() {
        val repo = FakeAuthRepository()
        assertEquals(AuthState.Unauthenticated, repo.authState.value)
        assertNull(repo.getCurrentUser())
    }

    @Test
    fun signInWithEmail_validCredentials_emitsAuthenticatedState() = kotlinx.coroutines.runBlocking {
        val repo = FakeAuthRepository()
        val result = repo.signInWithEmail("safety@safeher.org", "secret123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("safety@safeher.org", user.email)
        assertEquals("user_123", user.uid)
        assertTrue(repo.authState.value is AuthState.Authenticated)
    }

    @Test
    fun signInWithEmail_invalidEmailOrShortPassword_returnsError() = kotlinx.coroutines.runBlocking {
        val repo = FakeAuthRepository()

        val invalidEmailResult = repo.signInWithEmail("not-an-email", "secret123")
        assertTrue(invalidEmailResult is AuthResult.Error)

        val shortPasswordResult = repo.signInWithEmail("user@safeher.org", "123")
        assertTrue(shortPasswordResult is AuthResult.Error)

        assertEquals(AuthState.Unauthenticated, repo.authState.value)
    }

    @Test
    fun signUpWithEmail_validInput_createsUserAndAuthenticates() = kotlinx.coroutines.runBlocking {
        val repo = FakeAuthRepository()
        val result = repo.signUpWithEmail("newuser@safeher.org", "password123", "Alice Guardian")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("Alice Guardian", user.displayName)
        assertEquals("newuser@safeher.org", user.email)
    }

    @Test
    fun signOut_resetsAuthStateToUnauthenticated() = kotlinx.coroutines.runBlocking {
        val repo = FakeAuthRepository()
        repo.signInWithEmail("test@safeher.org", "password123")
        assertNotNull(repo.getCurrentUser())

        repo.signOut()
        assertNull(repo.getCurrentUser())
        assertEquals(AuthState.Unauthenticated, repo.authState.value)
    }

    @Test
    fun authUser_displayLabel_fallsBackGracefully() {
        val userWithDisplayName = AuthUser(uid = "1", email = "test@safeher.org", displayName = "Jane Doe")
        assertEquals("Jane Doe", userWithDisplayName.displayLabel)

        val userWithoutDisplayName = AuthUser(uid = "2", email = "jane.smith@safeher.org", displayName = null)
        assertEquals("jane.smith", userWithoutDisplayName.displayLabel)

        val anonymousUser = AuthUser(uid = "3", email = null, displayName = null)
        assertEquals("SafeHer User", anonymousUser.displayLabel)
    }
}
