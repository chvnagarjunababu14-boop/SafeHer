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

    fun isDemoMode(context: Context): Boolean {
        return DemoModeManager.getInstance(context).isDemoMode.value
    }

    fun getDemoModeManager(context: Context): DemoModeManager {
        return DemoModeManager.getInstance(context)
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
        val db = SafeHerDatabase.getInstance(context.applicationContext)
        return GuardianRepositoryImpl(db.guardianDao())
    }

    fun getJourneyRepository(context: Context): JourneyRepository {
        val db = SafeHerDatabase.getInstance(context.applicationContext)
        return JourneyRepositoryImpl(db)
    }

    fun getLocationRepository(context: Context): LocationRepository {
        return FusedLocationRepository(context.applicationContext)
    }
}
