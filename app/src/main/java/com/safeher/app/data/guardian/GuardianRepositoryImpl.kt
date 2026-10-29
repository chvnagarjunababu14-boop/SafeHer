package com.safeher.app.data.guardian

import com.safeher.app.data.local.db.GuardianDao
import com.safeher.app.data.local.db.GuardianEntity
import com.safeher.app.data.phone.CountryInfo
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneParseResult
import com.safeher.app.data.phone.PhoneNumberHelper
import com.safeher.app.data.session.UserSessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [GuardianRepository] backed by Room SQLite database.
 * Enforces account isolation, privacy, phone number normalization, and strict validation.
 */
class GuardianRepositoryImpl(
    private val dao: GuardianDao,
    private val sessionManager: UserSessionManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GuardianRepository {

    constructor(
        dao: GuardianDao,
        ioDispatcher: CoroutineDispatcher
    ) : this(dao, null, ioDispatcher)

    private val updateTrigger = MutableStateFlow(System.currentTimeMillis())

    override fun getGuardiansFlow(): Flow<List<Guardian>> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            withContext(ioDispatcher) {
                if (sessionManager != null && uid == null) {
                    emptyList()
                } else if (uid != null) {
                    dao.getGuardiansForUser(uid).map { it.toDomain() }
                } else {
                    dao.allGuardians.map { it.toDomain() }
                }
            }
        }.flowOn(ioDispatcher)
    }

    override fun getGuardianCountFlow(): Flow<Int> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            withContext(ioDispatcher) {
                if (sessionManager != null && uid == null) {
                    0
                } else if (uid != null) {
                    dao.getGuardianCountForUser(uid)
                } else {
                    dao.guardianCount
                }
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun getGuardians(): List<Guardian> = withContext(ioDispatcher) {
        if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext emptyList()
            dao.getGuardiansForUser(uid).map { it.toDomain() }
        } else {
            dao.allGuardians.map { it.toDomain() }
        }
    }

    override suspend fun getGuardianCount(): Int = withContext(ioDispatcher) {
        if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext 0
            dao.getGuardianCountForUser(uid)
        } else {
            dao.guardianCount
        }
    }

    override suspend fun getGuardianById(id: Long): Guardian? = withContext(ioDispatcher) {
        if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext null
            dao.getGuardianByIdForUser(id, uid)?.toDomain()
        } else {
            dao.getGuardianById(id)?.toDomain()
        }
    }

    override suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult = withContext(ioDispatcher) {
        val currentUid = sessionManager?.currentUserId
        if (sessionManager != null && currentUid == null) {
            return@withContext GuardianOperationResult.Error("Cannot add guardian when signed out")
        }

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext GuardianOperationResult.ValidationError("name", "Name is required")
        }
        if (trimmedName.length < 2) {
            return@withContext GuardianOperationResult.ValidationError("name", "Name must be at least 2 characters")
        }

        val parseResult = PhoneNumberHelper.parseAndValidate(phoneNumber, CountryRegistry.DEFAULT_COUNTRY)
        val normalizedPhone = when (parseResult) {
            is PhoneParseResult.Valid -> parseResult.e164
            is PhoneParseResult.Invalid -> {
                return@withContext GuardianOperationResult.ValidationError("phoneNumber", parseResult.reason)
            }
        }

        // Check for duplicates in current user's circle
        val existing = if (currentUid != null) {
            dao.getGuardianByPhoneNumberForUser(normalizedPhone, currentUid)
        } else {
            dao.getGuardianByPhoneNumber(normalizedPhone)
        }
        if (existing != null) {
            return@withContext GuardianOperationResult.DuplicatePhone(
                "A guardian with phone number $normalizedPhone is already registered in your circle."
            )
        }

        val now = System.currentTimeMillis()
        val entity = GuardianEntity(
            0,
            currentUid,
            trimmedName,
            normalizedPhone,
            relationship?.trim()?.ifBlank { null },
            false, // Never verified on creation
            now,
            now,
            0L,
            true // isSosEnabled defaults to true
        )

        return@withContext try {
            val insertedId = dao.insert(entity)
            notifyDataChanged()
            GuardianOperationResult.Success(insertedId)
        } catch (e: Exception) {
            GuardianOperationResult.Error(e.localizedMessage ?: "Failed to save guardian locally")
        }
    }

    override suspend fun updateGuardian(
        id: Long,
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult = withContext(ioDispatcher) {
        val currentUid = sessionManager?.currentUserId
        if (sessionManager != null && currentUid == null) {
            return@withContext GuardianOperationResult.Error("Cannot update guardian when signed out")
        }

        val existing = if (currentUid != null) {
            dao.getGuardianByIdForUser(id, currentUid)
        } else {
            dao.getGuardianById(id)
        } ?: return@withContext GuardianOperationResult.Error("Guardian not found")

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext GuardianOperationResult.ValidationError("name", "Name is required")
        }
        if (trimmedName.length < 2) {
            return@withContext GuardianOperationResult.ValidationError("name", "Name must be at least 2 characters")
        }

        val parseResult = PhoneNumberHelper.parseAndValidate(phoneNumber, CountryRegistry.DEFAULT_COUNTRY)
        val normalizedPhone = when (parseResult) {
            is PhoneParseResult.Valid -> parseResult.e164
            is PhoneParseResult.Invalid -> {
                return@withContext GuardianOperationResult.ValidationError("phoneNumber", parseResult.reason)
            }
        }

        // Check if phone changed and collides with another guardian for this user
        val phoneHolder = if (currentUid != null) {
            dao.getGuardianByPhoneNumberForUser(normalizedPhone, currentUid)
        } else {
            dao.getGuardianByPhoneNumber(normalizedPhone)
        }
        if (phoneHolder != null && phoneHolder.id != id) {
            return@withContext GuardianOperationResult.DuplicatePhone(
                "Another guardian is already registered with phone number $normalizedPhone."
            )
        }

        val now = System.currentTimeMillis()
        val updated = GuardianEntity(
            existing.id,
            currentUid ?: existing.userId,
            trimmedName,
            normalizedPhone,
            relationship?.trim()?.ifBlank { null },
            existing.isVerified && existing.phoneNumber == normalizedPhone, // Invalidate verification if phone changed
            existing.createdAt,
            now,
            if (existing.phoneNumber == normalizedPhone) existing.verifiedAt else 0L,
            existing.isSosEnabled,
            existing.isBatteryAlertEnabled
        )

        return@withContext try {
            dao.update(updated)
            notifyDataChanged()
            GuardianOperationResult.Success(id)
        } catch (e: Exception) {
            GuardianOperationResult.Error(e.localizedMessage ?: "Failed to update guardian")
        }
    }

    override suspend fun updateSosEnabled(id: Long, isSosEnabled: Boolean): Boolean = withContext(ioDispatcher) {
        val currentUid = sessionManager?.currentUserId
        if (sessionManager != null && currentUid == null) {
            return@withContext false
        }

        val rows = if (currentUid != null) {
            dao.updateSosEnabledForUser(id, currentUid, isSosEnabled, System.currentTimeMillis())
        } else {
            val guardian = dao.getGuardianById(id) ?: return@withContext false
            guardian.isSosEnabled = isSosEnabled
            guardian.updatedAt = System.currentTimeMillis()
            dao.update(guardian)
            1
        }
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    override suspend fun getSosEnabledGuardians(): List<Guardian> = withContext(ioDispatcher) {
        if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext emptyList()
            dao.getSosEnabledGuardiansForUser(uid).map { it.toDomain() }
        } else {
            dao.allSosEnabledGuardians.map { it.toDomain() }
        }
    }

    override fun getSosEnabledGuardiansFlow(): Flow<List<Guardian>> {
        val sessionFlow = sessionManager?.activeUserId ?: MutableStateFlow(null)
        return combine(sessionFlow, updateTrigger) { uid, _ ->
            withContext(ioDispatcher) {
                if (sessionManager != null && uid == null) {
                    emptyList()
                } else if (uid != null) {
                    dao.getSosEnabledGuardiansForUser(uid).map { it.toDomain() }
                } else {
                    dao.allSosEnabledGuardians.map { it.toDomain() }
                }
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun updateBatteryAlertEnabled(id: Long, isBatteryAlertEnabled: Boolean): Boolean =
        withContext(ioDispatcher) {
            val currentUid = sessionManager?.currentUserId
            if (sessionManager != null && currentUid == null) {
                return@withContext false
            }

            val rows = if (currentUid != null) {
                dao.updateBatteryAlertEnabledForUser(
                    id,
                    currentUid,
                    isBatteryAlertEnabled,
                    System.currentTimeMillis()
                )
            } else {
                val guardian = dao.getGuardianById(id) ?: return@withContext false
                guardian.isBatteryAlertEnabled = isBatteryAlertEnabled
                guardian.updatedAt = System.currentTimeMillis()
                dao.update(guardian)
                1
            }
            if (rows > 0) {
                notifyDataChanged()
                true
            } else {
                false
            }
        }

    override suspend fun getBatteryAlertEnabledGuardians(): List<Guardian> = withContext(ioDispatcher) {
        if (sessionManager != null) {
            val uid = sessionManager.currentUserId ?: return@withContext emptyList()
            dao.getBatteryAlertEnabledGuardiansForUser(uid).map { it.toDomain() }
        } else {
            dao.allBatteryAlertEnabledGuardians.map { it.toDomain() }
        }
    }

    override suspend fun deleteGuardian(id: Long): Boolean = withContext(ioDispatcher) {
        val currentUid = sessionManager?.currentUserId
        if (sessionManager != null && currentUid == null) {
            return@withContext false
        }

        val rows = if (currentUid != null) {
            dao.deleteByIdForUser(id, currentUid)
        } else {
            dao.deleteById(id)
        }
        if (rows > 0) {
            notifyDataChanged()
            true
        } else {
            false
        }
    }

    private fun notifyDataChanged() {
        updateTrigger.value = System.currentTimeMillis()
    }

    companion object {
        fun normalizePhoneNumber(raw: String, defaultCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY): String {
            return when (val res = PhoneNumberHelper.parseAndValidate(raw, defaultCountry)) {
                is PhoneParseResult.Valid -> res.e164
                is PhoneParseResult.Invalid -> {
                    val trimmed = raw.trim()
                    val digitsOnly = trimmed.filter { it.isDigit() }
                    if (trimmed.startsWith("+")) "+$digitsOnly" else digitsOnly
                }
            }
        }

        fun validatePhoneNumber(raw: String, defaultCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY): String? {
            return when (val res = PhoneNumberHelper.parseAndValidate(raw, defaultCountry)) {
                is PhoneParseResult.Valid -> null
                is PhoneParseResult.Invalid -> res.reason
            }
        }
    }

    private fun GuardianEntity.toDomain(): Guardian = Guardian(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        relationship = relationship,
        isVerified = isVerified,
        createdAt = createdAt,
        updatedAt = updatedAt,
        verifiedAt = if (verifiedAt > 0) verifiedAt else null,
        isSosEnabled = isSosEnabled,
        isBatteryAlertEnabled = isBatteryAlertEnabled
    )
}
