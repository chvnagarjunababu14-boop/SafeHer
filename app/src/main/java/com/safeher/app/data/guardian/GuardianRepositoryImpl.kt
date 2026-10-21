package com.safeher.app.data.guardian

import com.safeher.app.data.local.db.GuardianDao
import com.safeher.app.data.local.db.GuardianEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.safeher.app.data.phone.CountryInfo
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneParseResult
import com.safeher.app.data.phone.PhoneNumberHelper

/**
 * Concrete implementation of [GuardianRepository] backed by Room SQLite database.
 * Enforces privacy, phone number normalization, and strict validation.
 */
class GuardianRepositoryImpl(
    private val dao: GuardianDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GuardianRepository {

    private val updateTrigger = MutableStateFlow(System.currentTimeMillis())

    override fun getGuardiansFlow(): Flow<List<Guardian>> {
        return updateTrigger.map {
            getGuardians()
        }
    }

    override fun getGuardianCountFlow(): Flow<Int> {
        return updateTrigger.map {
            getGuardianCount()
        }
    }

    override suspend fun getGuardians(): List<Guardian> = withContext(ioDispatcher) {
        dao.allGuardians.map { it.toDomain() }
    }

    override suspend fun getGuardianCount(): Int = withContext(ioDispatcher) {
        dao.guardianCount
    }

    override suspend fun getGuardianById(id: Long): Guardian? = withContext(ioDispatcher) {
        dao.getGuardianById(id)?.toDomain()
    }

    override suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult = withContext(ioDispatcher) {
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

        // Check for duplicates
        val existing = dao.getGuardianByPhoneNumber(normalizedPhone)
        if (existing != null) {
            return@withContext GuardianOperationResult.DuplicatePhone(
                "A guardian with phone number $normalizedPhone is already registered in your circle."
            )
        }

        val now = System.currentTimeMillis()
        val entity = GuardianEntity(
            0,
            trimmedName,
            normalizedPhone,
            relationship?.trim()?.ifBlank { null },
            false, // Never verified on creation
            now,
            now,
            0L
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
        val existing = dao.getGuardianById(id)
            ?: return@withContext GuardianOperationResult.Error("Guardian not found")

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

        // Check if phone changed and collides with another guardian
        val phoneHolder = dao.getGuardianByPhoneNumber(normalizedPhone)
        if (phoneHolder != null && phoneHolder.id != id) {
            return@withContext GuardianOperationResult.DuplicatePhone(
                "Another guardian is already registered with phone number $normalizedPhone."
            )
        }

        val now = System.currentTimeMillis()
        val updated = GuardianEntity(
            existing.id,
            trimmedName,
            normalizedPhone,
            relationship?.trim()?.ifBlank { null },
            existing.isVerified && existing.phoneNumber == normalizedPhone, // Invalidate verification if phone changed
            existing.createdAt,
            now,
            if (existing.phoneNumber == normalizedPhone) existing.verifiedAt else 0L
        )

        return@withContext try {
            dao.update(updated)
            notifyDataChanged()
            GuardianOperationResult.Success(id)
        } catch (e: Exception) {
            GuardianOperationResult.Error(e.localizedMessage ?: "Failed to update guardian")
        }
    }

    override suspend fun deleteGuardian(id: Long): Boolean = withContext(ioDispatcher) {
        val rows = dao.deleteById(id)
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
        verifiedAt = if (verifiedAt > 0) verifiedAt else null
    )
}
