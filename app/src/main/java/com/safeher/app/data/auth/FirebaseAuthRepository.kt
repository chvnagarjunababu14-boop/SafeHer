package com.safeher.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    init {
        val auth = firebaseAuth
        if (auth != null) {
            auth.addAuthStateListener { fbAuth ->
                val currentUser = fbAuth.currentUser
                if (currentUser != null) {
                    _authState.value = AuthState.Authenticated(currentUser.toAuthUser())
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        } else {
            // Firebase not initialized on device (e.g. google-services.json not yet placed)
            _authState.value = AuthState.Unauthenticated
        }
    }

    override fun getCurrentUser(): AuthUser? {
        return firebaseAuth?.currentUser?.toAuthUser()
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        withContext(dispatcher) {
            val auth = firebaseAuth ?: return@withContext AuthResult.Error(
                "Firebase is not configured yet. Please add google-services.json to enable remote features."
            )

            val trimmedEmail = email.trim()
            if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                return@withContext AuthResult.Error("Please enter a valid email address.")
            }
            if (password.length < 6) {
                return@withContext AuthResult.Error("Password must be at least 6 characters.")
            }

            try {
                val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                val user = result.user?.toAuthUser()
                if (user != null) {
                    AuthResult.Success(user)
                } else {
                    AuthResult.Error("Sign in failed. Could not retrieve user profile.")
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                // Prevent email enumeration
                AuthResult.Error("Invalid email or password.")
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                AuthResult.Error("Invalid email or password.")
            } catch (e: Exception) {
                AuthResult.Error(e.localizedMessage ?: "Authentication failed. Please check network connection.")
            }
        }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): AuthResult = withContext(dispatcher) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not configured yet. Please add google-services.json to enable remote features."
        )

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters.")
        }

        try {
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = result.user

            if (!displayName.isNullOrBlank() && firebaseUser != null) {
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                firebaseUser.updateProfile(profileUpdate).await()
            }

            val user = firebaseUser?.toAuthUser()
            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Account created, but could not load profile.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("An account with this email address already exists.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Registration failed. Please try again.")
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(dispatcher) {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase is not configured.")
            )

            val trimmedEmail = email.trim()
            if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid email."))
            }

            try {
                auth.sendPasswordResetEmail(trimmedEmail).await()
                Result.success(Unit)
            } catch (e: Exception) {
                // Even on error, avoid exposing whether email exists
                Result.success(Unit)
            }
        }

    override suspend fun sendEmailVerification(): Result<Unit> = withContext(dispatcher) {
        val auth = firebaseAuth ?: return@withContext Result.failure(
            IllegalStateException("Firebase is not configured.")
        )
        val user = auth.currentUser ?: return@withContext Result.failure(
            IllegalStateException("No signed-in user.")
        )

        try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(context: Context, webClientId: String): AuthResult =
        withContext(dispatcher) {
            val auth = firebaseAuth ?: return@withContext AuthResult.Error(
                "Firebase is not configured yet. Please add google-services.json to enable remote features."
            )

            if (webClientId.isBlank()) {
                return@withContext AuthResult.Error(
                    "Google Sign-In is not configured yet. OAuth Web Client ID is required."
                )
            }

            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCredential).await()
                    val user = authResult.user?.toAuthUser()

                    if (user != null) {
                        AuthResult.Success(user)
                    } else {
                        AuthResult.Error("Google Sign-In completed, but user profile could not be loaded.")
                    }
                } else {
                    AuthResult.Error("Unexpected credential type received.")
                }
            } catch (e: GetCredentialCancellationException) {
                AuthResult.Cancelled
            } catch (e: GetCredentialException) {
                AuthResult.Error("Google Sign-In failed: ${e.message ?: "Unknown error"}")
            } catch (e: Exception) {
                AuthResult.Error(e.localizedMessage ?: "Google Authentication failed.")
            }
        }

    override suspend fun signOut() = withContext(dispatcher) {
        firebaseAuth?.signOut()
        _authState.value = AuthState.Unauthenticated
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isEmailVerified = isEmailVerified,
            isAnonymous = isAnonymous
        )
    }
}
