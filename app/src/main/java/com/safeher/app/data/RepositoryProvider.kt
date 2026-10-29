package com.safeher.app.data

import android.content.Context
import com.safeher.app.data.auth.AuthRepository
import com.safeher.app.data.auth.FirebaseAuthRepository
import com.safeher.app.data.demo.DemoModeManager
import com.safeher.app.data.demo.DemoRepositoryContainer
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.guardian.GuardianRepositoryImpl
import com.safeher.app.data.journey.JourneyRepository
import com.safeher.app.data.journey.JourneyRepositoryImpl
import com.safeher.app.data.local.db.SafeHerDatabase
import com.safeher.app.data.location.FusedLocationRepository
import com.safeher.app.data.location.LocationRepository
import com.safeher.app.data.remoteguardian.FirebaseRemoteGuardianRepository
import com.safeher.app.data.remoteguardian.RemoteGuardianRepository
import com.safeher.app.data.remotesos.FirebaseRemoteSosRepository
import com.safeher.app.data.remotesos.RemoteSosRepository
import com.safeher.app.data.session.UserSessionManager
import com.safeher.app.data.battery.AndroidBatteryStateReader
import com.safeher.app.data.battery.BatteryAlertManager
import com.safeher.app.data.battery.BatteryAlertStateStore
import com.safeher.app.data.sos.AndroidDirectSmsSender
import com.safeher.app.data.sos.DemoDirectSmsSender
import com.safeher.app.data.sos.DirectSmsSender
import com.safeher.app.data.sos.DirectSosManager
import com.safeher.app.data.sos.SosPreferencesRepository
import com.safeher.app.data.sos.SosPreferencesRepositoryImpl

/**
 * Authoritative provider and service locator for SafeHer repositories.
 *
 * Implements a clean repository boundary that switches seamlessly between:
 * 1. Demo Mode: Local, self-contained mock repositories without Firebase or network dependencies.
 * 2. Production Mode: Real Firebase Authentication, Cloud Firestore, Cloud Functions, and FCM.
 *
 * Always preserves local Room database and foreground location retrieval in both modes.
 */
object RepositoryProvider {

    @Volatile
    private var sessionManagerInstance: UserSessionManager? = null

    @Volatile
    private var guardianRepoInstance: GuardianRepository? = null

    @Volatile
    private var journeyRepoInstance: JourneyRepository? = null

    fun isDemoMode(context: Context): Boolean {
        if (!com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) {
            return false
        }
        return DemoModeManager.getInstance(context).isDemoMode.value
    }

    fun getDemoModeManager(context: Context): DemoModeManager {
        return DemoModeManager.getInstance(context)
    }

    fun getUserSessionManager(context: Context): UserSessionManager {
        return sessionManagerInstance ?: synchronized(this) {
            sessionManagerInstance ?: UserSessionManager.getInstance(context).also { manager ->
                val authRepo = getAuthRepository(context)
                val demoManager = if (com.safeher.app.BuildConfig.IS_DEMO_DEFAULT) getDemoModeManager(context) else null
                manager.initialize(authRepo, demoManager)
                sessionManagerInstance = manager
            }
        }
    }

    fun getAuthRepository(context: Context): AuthRepository {
        return if (isDemoMode(context)) {
            DemoRepositoryContainer.getAuthRepository(context)
        } else {
            FirebaseAuthRepository(context.applicationContext)
        }
    }

    fun getRemoteGuardianRepository(context: Context): RemoteGuardianRepository {
        return if (isDemoMode(context)) {
            DemoRepositoryContainer.getRemoteGuardianRepository(context)
        } else {
            FirebaseRemoteGuardianRepository(context.applicationContext)
        }
    }

    fun getRemoteSosRepository(context: Context): RemoteSosRepository {
        return if (isDemoMode(context)) {
            DemoRepositoryContainer.getRemoteSosRepository(context)
        } else {
            FirebaseRemoteSosRepository(context.applicationContext)
        }
    }

    fun getGuardianRepository(context: Context): GuardianRepository {
        return guardianRepoInstance ?: synchronized(this) {
            guardianRepoInstance ?: run {
                val db = SafeHerDatabase.getInstance(context.applicationContext)
                val session = getUserSessionManager(context)
                GuardianRepositoryImpl(db.guardianDao(), session).also { guardianRepoInstance = it }
            }
        }
    }

    fun getJourneyRepository(context: Context): JourneyRepository {
        return journeyRepoInstance ?: synchronized(this) {
            journeyRepoInstance ?: run {
                val db = SafeHerDatabase.getInstance(context.applicationContext)
                val session = getUserSessionManager(context)
                val scheduler = com.safeher.app.data.journey.JourneyMonitoringScheduler(context.applicationContext)
                JourneyRepositoryImpl(db, session, scheduler).also { journeyRepoInstance = it }
            }
        }
    }

    fun getLocationRepository(context: Context): LocationRepository {
        return FusedLocationRepository(context.applicationContext)
    }

    @Volatile
    private var sosPrefsInstance: SosPreferencesRepository? = null

    fun getSosPreferencesRepository(context: Context): SosPreferencesRepository {
        return sosPrefsInstance ?: synchronized(this) {
            sosPrefsInstance ?: run {
                val session = getUserSessionManager(context)
                SosPreferencesRepositoryImpl.create(context.applicationContext, session).also {
                    sosPrefsInstance = it
                }
            }
        }
    }

    @Volatile
    private var directSmsSenderInstance: DirectSmsSender? = null

    fun getDirectSmsSender(context: Context): DirectSmsSender {
        return directSmsSenderInstance ?: synchronized(this) {
            directSmsSenderInstance ?: run {
                val sender = if (isDemoMode(context)) {
                    DemoDirectSmsSender()
                } else {
                    AndroidDirectSmsSender(context.applicationContext)
                }
                sender.also { directSmsSenderInstance = it }
            }
        }
    }

    @Volatile
    private var directSosManagerInstance: DirectSosManager? = null

    fun getDirectSosManager(context: Context): DirectSosManager {
        return directSosManagerInstance ?: synchronized(this) {
            directSosManagerInstance ?: run {
                val db = SafeHerDatabase.getInstance(context.applicationContext)
                val manager = DirectSosManager(
                    guardianRepository = getGuardianRepository(context),
                    sosPreferencesRepository = getSosPreferencesRepository(context),
                    directSmsSender = getDirectSmsSender(context),
                    locationRepository = getLocationRepository(context),
                    sosEventDao = db.sosEventDao(),
                    sessionManager = getUserSessionManager(context),
                    batteryStateReader = AndroidBatteryStateReader(context.applicationContext)
                )
                manager.also { directSosManagerInstance = it }
            }
        }
    }

    @Volatile
    private var batteryAlertManagerInstance: BatteryAlertManager? = null

    fun getBatteryAlertManager(context: Context): BatteryAlertManager {
        return batteryAlertManagerInstance ?: synchronized(this) {
            batteryAlertManagerInstance ?: run {
                val db = SafeHerDatabase.getInstance(context.applicationContext)
                val session = getUserSessionManager(context)
                BatteryAlertManager(
                    guardianRepository = getGuardianRepository(context),
                    sosPreferencesRepository = getSosPreferencesRepository(context),
                    directSmsSender = getDirectSmsSender(context),
                    sosEventDao = db.sosEventDao(),
                    stateStore = BatteryAlertStateStore.create(context.applicationContext, session),
                    locationRepository = getLocationRepository(context),
                    sessionManager = session
                ).also { batteryAlertManagerInstance = it }
            }
        }
    }

    @Volatile
    private var onboardingPrefsInstance: com.safeher.app.data.onboarding.OnboardingPreferencesRepository? = null

    fun getOnboardingPreferencesRepository(context: Context): com.safeher.app.data.onboarding.OnboardingPreferencesRepository {
        return onboardingPrefsInstance ?: synchronized(this) {
            onboardingPrefsInstance ?: com.safeher.app.data.onboarding.OnboardingPreferencesRepositoryImpl.create(context).also {
                onboardingPrefsInstance = it
            }
        }
    }
}
