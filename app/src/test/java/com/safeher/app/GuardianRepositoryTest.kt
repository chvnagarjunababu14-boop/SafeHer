package com.safeher.app

import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepositoryImpl
import com.safeher.app.data.guardian.GuardianVerificationState
import com.safeher.app.data.local.db.GuardianDao
import com.safeher.app.data.local.db.GuardianEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDao : GuardianDao {
    private val storage = mutableListOf<GuardianEntity>()
    private var idCounter = 1L

    override fun getAllGuardians(): List<GuardianEntity> = storage.toList()
    override fun getGuardianCount(): Int = storage.size
    override fun getGuardianById(id: Long): GuardianEntity? = storage.find { it.id == id }
    override fun getGuardianByPhoneNumber(phoneNumber: String): GuardianEntity? =
        storage.find { it.phoneNumber == phoneNumber }

    override fun getGuardiansForUser(userId: String): List<GuardianEntity> =
        storage.filter { it.userId == userId }

    override fun getGuardianCountForUser(userId: String): Int =
        storage.count { it.userId == userId }

    override fun getGuardianByIdForUser(id: Long, userId: String): GuardianEntity? =
        storage.find { it.id == id && it.userId == userId }

    override fun getGuardianByPhoneNumberForUser(phoneNumber: String, userId: String): GuardianEntity? =
        storage.find { it.phoneNumber == phoneNumber && it.userId == userId }

    override fun deleteByIdForUser(id: Long, userId: String): Int =
        if (storage.removeIf { it.id == id && it.userId == userId }) 1 else 0

    override fun getUnassignedGuardians(): List<GuardianEntity> =
        storage.filter { it.userId == null }

    override fun getUnassignedGuardianCount(): Int =
        storage.count { it.userId == null }

    override fun assignUnassignedGuardiansToUser(userId: String): Int {
        var count = 0
        storage.forEachIndexed { idx, entity ->
            if (entity.userId == null) {
                storage[idx] = GuardianEntity(
                    entity.id,
                    userId,
                    entity.name,
                    entity.phoneNumber,
                    entity.relationship,
                    entity.isVerified,
                    entity.createdAt,
                    entity.updatedAt,
                    entity.verifiedAt,
                    entity.isSosEnabled,
                    entity.isBatteryAlertEnabled
                )
                count++
            }
        }
        return count
    }

    override fun getSosEnabledGuardiansForUser(userId: String): List<GuardianEntity> =
        storage.filter { it.userId == userId && it.isSosEnabled }

    override fun updateSosEnabledForUser(id: Long, userId: String, isSosEnabled: Boolean, updatedAt: Long): Int {
        val idx = storage.indexOfFirst { it.id == id && it.userId == userId }
        if (idx != -1) {
            storage[idx].isSosEnabled = isSosEnabled
            storage[idx].updatedAt = updatedAt
            return 1
        }
        return 0
    }

    override fun getAllSosEnabledGuardians(): List<GuardianEntity> =
        storage.filter { it.isSosEnabled }

    override fun getBatteryAlertEnabledGuardiansForUser(userId: String): List<GuardianEntity> =
        storage.filter { it.userId == userId && it.isBatteryAlertEnabled }

    override fun updateBatteryAlertEnabledForUser(
        id: Long,
        userId: String,
        isBatteryAlertEnabled: Boolean,
        updatedAt: Long
    ): Int {
        val idx = storage.indexOfFirst { it.id == id && it.userId == userId }
        if (idx != -1) {
            storage[idx].isBatteryAlertEnabled = isBatteryAlertEnabled
            storage[idx].updatedAt = updatedAt
            return 1
        }
        return 0
    }

    override fun getAllBatteryAlertEnabledGuardians(): List<GuardianEntity> =
        storage.filter { it.isBatteryAlertEnabled }

    override fun insert(guardian: GuardianEntity): Long {
        val assigned = GuardianEntity(
            idCounter++,
            guardian.userId,
            guardian.name,
            guardian.phoneNumber,
            guardian.relationship,
            guardian.isVerified,
            guardian.createdAt,
            guardian.updatedAt,
            guardian.verifiedAt,
            guardian.isSosEnabled,
            guardian.isBatteryAlertEnabled
        )
        storage.add(assigned)
        return assigned.id
    }

    override fun update(guardian: GuardianEntity): Int {
        val idx = storage.indexOfFirst { it.id == guardian.id }
        if (idx != -1) {
            storage[idx] = guardian
            return 1
        }
        return 0
    }

    override fun delete(guardian: GuardianEntity): Int {
        return if (storage.removeIf { it.id == guardian.id }) 1 else 0
    }

    override fun deleteById(id: Long): Int {
        return if (storage.removeIf { it.id == id }) 1 else 0
    }
}

class GuardianRepositoryTest {

    @Test
    fun normalizePhoneNumber_handlesVariousFormatsToCanonicalE164() {
        assertEquals("+15550001111", GuardianRepositoryImpl.normalizePhoneNumber("+1 (555) 000-1111"))
        assertEquals("+919876543210", GuardianRepositoryImpl.normalizePhoneNumber("987-654-3210"))
        assertEquals("+919876543210", GuardianRepositoryImpl.normalizePhoneNumber("+91 98765 43210"))
        assertEquals("", GuardianRepositoryImpl.normalizePhoneNumber("   "))
    }

    @Test
    fun validatePhoneNumber_identifiesInvalidLengths() {
        assertNotNull(GuardianRepositoryImpl.validatePhoneNumber(""))
        assertNotNull(GuardianRepositoryImpl.validatePhoneNumber("12345")) // too short
        assertNull(GuardianRepositoryImpl.validatePhoneNumber("9876543210")) // valid 10 digits India
        assertNull(GuardianRepositoryImpl.validatePhoneNumber("+12025550123")) // valid US number
        assertNotNull(GuardianRepositoryImpl.validatePhoneNumber("123456789012345678")) // too long
    }

    @Test
    fun addGuardian_persistsToDaoAndDefaultsToUnverified() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        val result = repo.addGuardian(
            name = "John Doe",
            phoneNumber = "+1 (555) 000-1111",
            relationship = "Friend"
        )

        assertTrue(result is GuardianOperationResult.Success)
        val id = (result as GuardianOperationResult.Success).guardianId
        assertEquals(1, repo.getGuardianCount())

        val saved = repo.getGuardianById(id)
        assertNotNull(saved)
        assertEquals("John Doe", saved?.name)
        assertEquals("+15550001111", saved?.phoneNumber)
        assertEquals("Friend", saved?.relationship)
        assertEquals(false, saved?.isVerified)
    }

    @Test
    fun addGuardian_duplicatePhone_rejectedAcrossEquivalentFormats() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        // Add with local format
        val first = repo.addGuardian("Alice", "9876543210", "Sister")
        assertTrue(first is GuardianOperationResult.Success)

        // Try adding equivalent number with international + spaces
        val duplicateResult = repo.addGuardian("Bob", "+91 98765 43210", "Friend")
        assertTrue(duplicateResult is GuardianOperationResult.DuplicatePhone)
        assertEquals(1, repo.getGuardianCount())
    }

    @Test
    fun deleteGuardian_removesRecord() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        val result = repo.addGuardian("Alice", "9876543210", "Sister")
        val id = (result as GuardianOperationResult.Success).guardianId
        assertEquals(1, repo.getGuardianCount())

        val deleted = repo.deleteGuardian(id)
        assertTrue(deleted)
        assertEquals(0, repo.getGuardianCount())
    }

    @Test
    fun updateGuardian_updatesDetailsAndPreservesId() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        val addResult = repo.addGuardian("Alice", "9876543210", "Sister")
        val id = (addResult as GuardianOperationResult.Success).guardianId

        val updateResult = repo.updateGuardian(
            id = id,
            name = "Alice Smith",
            phoneNumber = "9876543210",
            relationship = "Mother"
        )
        assertTrue(updateResult is GuardianOperationResult.Success)

        val updated = repo.getGuardianById(id)
        assertNotNull(updated)
        assertEquals("Alice Smith", updated?.name)
        assertEquals("+919876543210", updated?.phoneNumber)
        assertEquals("Mother", updated?.relationship)
    }

    @Test
    fun updateGuardian_duplicatePhone_rejectedWhenCollidesWithAnotherGuardian() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        val first = repo.addGuardian("Alice", "9876543210", "Sister")
        val second = repo.addGuardian("Bob", "9876500000", "Friend")
        val secondId = (second as GuardianOperationResult.Success).guardianId

        // Try updating Bob's phone to Alice's phone
        val updateResult = repo.updateGuardian(
            id = secondId,
            name = "Bob",
            phoneNumber = "+91 98765 43210",
            relationship = "Friend"
        )
        assertTrue(updateResult is GuardianOperationResult.DuplicatePhone)
    }

    @Test
    fun guardianVerificationState_unverifiedByDefault() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        val addResult = repo.addGuardian("Alice", "9876543210", "Sister")
        val id = (addResult as GuardianOperationResult.Success).guardianId

        val saved = repo.getGuardianById(id)
        assertNotNull(saved)
        assertEquals(false, saved?.isVerified)
        assertEquals(GuardianVerificationState.UNVERIFIED, saved?.verificationState)
    }

    @Test
    fun addGuardian_validationErrors_shortNameOrInvalidPhone() = runBlocking {
        val dao = FakeDao()
        val repo = GuardianRepositoryImpl(dao, Dispatchers.Unconfined)

        // Empty name
        val emptyNameResult = repo.addGuardian("", "9876543210", "Friend")
        assertTrue(emptyNameResult is GuardianOperationResult.ValidationError)
        assertEquals("name", (emptyNameResult as GuardianOperationResult.ValidationError).field)

        // Single-character name
        val shortNameResult = repo.addGuardian("A", "9876543210", "Friend")
        assertTrue(shortNameResult is GuardianOperationResult.ValidationError)
        assertEquals("name", (shortNameResult as GuardianOperationResult.ValidationError).field)

        // Invalid phone number
        val invalidPhoneResult = repo.addGuardian("Alice", "123", "Friend")
        assertTrue(invalidPhoneResult is GuardianOperationResult.ValidationError)
        assertEquals("phoneNumber", (invalidPhoneResult as GuardianOperationResult.ValidationError).field)
    }
}
