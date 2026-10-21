package com.safeher.app.data.demo

import android.content.Context

/**
 * Singleton container holding shared instances of Demo Mode repositories.
 */
object DemoRepositoryContainer {

    @Volatile
    private var authRepository: DemoAuthRepository? = null

    @Volatile
    private var remoteGuardianRepository: DemoRemoteGuardianRepository? = null

    @Volatile
    private var remoteSosRepository: DemoRemoteSosRepository? = null

    fun getAuthRepository(context: Context): DemoAuthRepository {
        return authRepository ?: synchronized(this) {
            authRepository ?: DemoAuthRepository(DemoModeManager.getInstance(context)).also {
                authRepository = it
            }
        }
    }

    fun getRemoteGuardianRepository(context: Context): DemoRemoteGuardianRepository {
        return remoteGuardianRepository ?: synchronized(this) {
            remoteGuardianRepository ?: DemoRemoteGuardianRepository(DemoModeManager.getInstance(context)).also {
                remoteGuardianRepository = it
            }
        }
    }

    fun getRemoteSosRepository(context: Context): DemoRemoteSosRepository {
        return remoteSosRepository ?: synchronized(this) {
            remoteSosRepository ?: DemoRemoteSosRepository(DemoModeManager.getInstance(context)).also {
                remoteSosRepository = it
            }
        }
    }

    fun reset() {
        authRepository = null
        remoteGuardianRepository = null
        remoteSosRepository = null
    }
}
