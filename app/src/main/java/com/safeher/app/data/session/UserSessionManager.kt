package com.safeher.app.data.session

import android.content.Context
import android.content.SharedPreferences
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.AuthState
import com.safeher.app.data.demo.DemoModeManager
import com.safeher.app.data.demo.DemoProfile
import com.safeher.app.data.local.db.SafeHerDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates the active authenticated session across Firebase Auth, Demo Mode,
 * and account-scoped Room persistence.
 *
 * Enforces strict account isolation:
 * 1. An active user ID is emitted whenever an account is signed in (or Demo Mode is active).
 * 2. When signed out, active user ID is null, causing all data flows to immediately hide private data.
 * 3. Legacy records from older schema versions (where userId was NULL) are safely preserved and
 *    only claimed by the verified legacy owner, NEVER by newly registered accounts.
 */
class UserSessionManager(
    private val prefs: SharedPreferences,
    private val databaseProvider: (() -> SafeHerDatabase)? = null,
    private val guardianDaoProvider: (() -> com.safeher.app.data.local.db.GuardianDao)? = null,
    private val journeyDaoProvider: (() -> com.safeher.app.data.local.db.JourneyDao)? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null
) {
    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + dispatcher)

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    /**
     * Incremented whenever a user role preference is written so UI can re-read
     * [getUserRole] after authentication without relying on a stale Compose remember cache.
     */
    private val _roleRevision = MutableStateFlow(0L)
    val roleRevision: StateFlow<Long> = _roleRevision.asStateFlow()

    /**
     * Role chosen on the Welcome slides before Firebase Auth completes.
     * Applied synchronously when auth becomes Authenticated so MainActivity never
     * routes using the PERSONAL_SAFETY_USER default while a Guardian login is in flight.
     */
    @Volatile
    private var pendingRoleForNextAuth: UserRole? = null

    /** When true, the next successful auth is treated as a brand-new registration for setup routing. */
    @Volatile
    private var pendingNewRegistration: Boolean = false

    val currentUserId: String?
        get() = _activeUserId.value

    /**
     * Captures the UI-selected role immediately before an auth attempt.
     * Established accounts keep their saved role; first-time accounts use this value.
     */
    fun setPendingRoleForNextAuthentication(role: UserRole) {
        pendingRoleForNextAuth = role
    }

    fun setPendingNewRegistration(isNew: Boolean) {
        pendingNewRegistration = isNew
    }

    fun clearPendingRoleForNextAuthentication() {
        pendingRoleForNextAuth = null
        pendingNewRegistration = false
    }

    private fun applyPendingAuthMetadataIfNeeded(userId: String) {
        if (pendingNewRegistration) {
            markAsNewRegistration(userId)
            pendingNewRegistration = false
        }
        val pending = pendingRoleForNextAuth
        pendingRoleForNextAuth = null
        if (pending == null) return
        val established = getEstablishedUserRole(userId)
        if (established != null) {
            // Returning account: keep established role; ignore UI slide selection.
            return
        }
        setUserRole(userId, pending)
    }

    fun initialize(authRepository: AuthRepository, demoModeManager: DemoModeManager? = null) {
        authRepository.authState
            .onEach { auth ->
                when (auth) {
                    is AuthState.Authenticated -> {
                        val uid = auth.user.uid
                        // Must run before publishing activeUserId so routing reads the correct role/setup flags.
                        applyPendingAuthMetadataIfNeeded(uid)
                        _activeUserId.value = uid
                        onUserAuthenticated(uid)
                    }
                    is AuthState.Unauthenticated -> {
                        _activeUserId.value = null
                        pendingRoleForNextAuth = null
                        pendingNewRegistration = false
                    }
                    AuthState.Loading -> {
                        // Keep current or null
                    }
                }
            }
            .launchIn(scope)

        if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            demoModeManager?.let { dmm ->
                dmm.isDemoMode
                    .onEach { isDemo ->
                        if (isDemo) {
                            _activeUserId.value = DemoProfile.USER.uid
                        } else {
                            val current = authRepository.getCurrentUser()
                            _activeUserId.value = current?.uid
                        }
                    }
                    .launchIn(scope)
            }
        }
    }

    /**
     * Explicitly set the active user ID (useful for unit tests or direct session changes).
     */
    fun setActiveUserId(userId: String?) {
        _activeUserId.value = userId
    }

    /**
     * Marks a user as a brand-new registration so they will NEVER inherit unassigned legacy records.
     */
    fun markAsNewRegistration(userId: String) {
        prefs.edit().putBoolean(KEY_NEW_REGISTRATION_PREFIX + userId, true).apply()
    }

    fun isNewRegistration(userId: String): Boolean {
        return prefs.getBoolean(KEY_NEW_REGISTRATION_PREFIX + userId, false)
    }

    /**
     * Checks whether an account already has an established role.
     * Legacy data owners and accounts with a previously saved role choice are established.
     */
    fun hasEstablishedRole(userId: String): Boolean {
        if (userId.isBlank()) return false
        if (userId == DemoProfile.GUARDIAN.uid || userId == DemoProfile.USER.uid) return true
        val legacyOwner = prefs.getString(KEY_LEGACY_OWNER_UID, null)
        if (legacyOwner == userId) return true
        return prefs.contains(KEY_ROLE_PREFIX + userId)
    }

    /**
     * Retrieves the established role if one exists, or null if this is a first-time account.
     */
    fun getEstablishedUserRole(userId: String): UserRole? {
        if (userId.isBlank()) return null
        if (userId == DemoProfile.GUARDIAN.uid) return UserRole.GUARDIAN
        if (userId == DemoProfile.USER.uid) return UserRole.PERSONAL_SAFETY_USER
        val legacyOwner = prefs.getString(KEY_LEGACY_OWNER_UID, null)
        if (legacyOwner == userId) return UserRole.PERSONAL_SAFETY_USER
        val saved = prefs.getString(KEY_ROLE_PREFIX + userId, null) ?: return null
        return when (saved) {
            UserRole.GUARDIAN.name -> UserRole.GUARDIAN
            UserRole.PERSONAL_SAFETY_USER.name -> UserRole.PERSONAL_SAFETY_USER
            else -> null
        }
    }

    /**
     * Retrieves the UI application role for a user account.
     * Defaults to [UserRole.PERSONAL_SAFETY_USER] for legacy and unassigned accounts.
     * Note: This is an application routing preference; actual security authorization is always
     * verified server-side against Firebase UID and backend relationship records.
     */
    fun getUserRole(userId: String): UserRole {
        if (userId.isBlank()) return UserRole.PERSONAL_SAFETY_USER
        if (userId == DemoProfile.GUARDIAN.uid) return UserRole.GUARDIAN
        if (userId == DemoProfile.USER.uid) return UserRole.PERSONAL_SAFETY_USER

        // The verified owner of legacy personal safety records is ALWAYS a Personal Safety User
        val legacyOwner = prefs.getString(KEY_LEGACY_OWNER_UID, null)
        if (legacyOwner == userId) return UserRole.PERSONAL_SAFETY_USER

        val saved = prefs.getString(KEY_ROLE_PREFIX + userId, null)
        return when (saved) {
            UserRole.GUARDIAN.name -> UserRole.GUARDIAN
            UserRole.PERSONAL_SAFETY_USER.name -> UserRole.PERSONAL_SAFETY_USER
            else -> UserRole.PERSONAL_SAFETY_USER
        }
    }

    /**
     * Persists the user's role choice scoped to their specific Firebase UID.
     * Established legacy personal safety owners are protected from being overwritten as Guardians.
     */
    fun setUserRole(userId: String, role: UserRole) {
        if (userId.isBlank()) return
        val legacyOwner = prefs.getString(KEY_LEGACY_OWNER_UID, null)
        if (legacyOwner == userId && role == UserRole.GUARDIAN) {
            // Guard legacy owner from ever being converted to Guardian
            prefs.edit().putString(KEY_ROLE_PREFIX + userId, UserRole.PERSONAL_SAFETY_USER.name).apply()
            _roleRevision.value = _roleRevision.value + 1
            return
        }
        prefs.edit().putString(KEY_ROLE_PREFIX + userId, role.name).apply()
        _roleRevision.value = _roleRevision.value + 1
    }

    private fun onUserAuthenticated(userId: String) {
        scope.launch {
            claimLegacyDataIfEligible(userId)
        }
    }

    /**
     * Safely associates unassigned legacy records (where userId is NULL) with the original owner.
     * Prevents newly registered accounts from inheriting legacy records.
     */
    suspend fun claimLegacyDataIfEligible(userId: String): Boolean = withContext(dispatcher) {
        if (userId == DemoProfile.USER.uid || userId.startsWith("demo_")) {
            // Demo accounts must NEVER claim production users' legacy records
            return@withContext false
        }
        if (isNewRegistration(userId)) {
            // Newly registered account: must see empty circle and empty history
            return@withContext false
        }

        val recordedOwner = prefs.getString(KEY_LEGACY_OWNER_UID, null)
        val validRecordedOwner = if (recordedOwner == DemoProfile.USER.uid || recordedOwner?.startsWith("demo_") == true) {
            prefs.edit().remove(KEY_LEGACY_OWNER_UID).apply()
            null
        } else {
            recordedOwner
        }
        if (validRecordedOwner != null && validRecordedOwner != userId) {
            // Unassigned legacy data belonged to a different account
            return@withContext false
        }

        val gDao = guardianDaoProvider?.invoke() ?: try {
            databaseProvider?.invoke()?.guardianDao()
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        val jDao = journeyDaoProvider?.invoke() ?: try {
            databaseProvider?.invoke()?.journeyDao()
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        val unassignedGuardians = gDao.unassignedGuardianCount
        val unassignedJourneys = jDao.unassignedJourneyCount

        if (unassignedGuardians > 0 || unassignedJourneys > 0) {
            // Claim legacy records for this verified returning user
            gDao.assignUnassignedGuardiansToUser(userId)
            jDao.assignUnassignedJourneysToUser(userId)
            prefs.edit().putString(KEY_LEGACY_OWNER_UID, userId).apply()
            return@withContext true
        } else if (recordedOwner == null) {
            prefs.edit().putString(KEY_LEGACY_OWNER_UID, userId).apply()
        }

        return@withContext false
    }

    companion object {
        const val PREFS_NAME = "safeher_user_session_prefs"
        const val KEY_LEGACY_OWNER_UID = "key_legacy_owner_uid"
        private const val KEY_NEW_REGISTRATION_PREFIX = "new_registration_"
        private const val KEY_ROLE_PREFIX = "user_role_"

        @Volatile
        private var INSTANCE: UserSessionManager? = null

        fun getInstance(
            context: Context,
            databaseProvider: () -> SafeHerDatabase = { SafeHerDatabase.getInstance(context) }
        ): UserSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserSessionManager(
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    databaseProvider = databaseProvider
                ).also { INSTANCE = it }
            }
        }

        fun createForTesting(
            prefs: SharedPreferences,
            databaseProvider: (() -> SafeHerDatabase)? = null,
            guardianDao: com.safeher.app.data.local.db.GuardianDao? = null,
            journeyDao: com.safeher.app.data.local.db.JourneyDao? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        ): UserSessionManager {
            return UserSessionManager(
                prefs = prefs,
                databaseProvider = databaseProvider,
                guardianDaoProvider = guardianDao?.let { { it } },
                journeyDaoProvider = journeyDao?.let { { it } },
                dispatcher = dispatcher
            )
        }
    }
}
