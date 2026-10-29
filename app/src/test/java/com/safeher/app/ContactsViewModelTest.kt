package com.safeher.app

import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.guardian.GuardianRepositoryImpl
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneNumberHelper
import com.safeher.app.ui.screens.contacts.ContactImportResult
import com.safeher.app.ui.screens.contacts.ContactPermissionDialogState
import com.safeher.app.ui.screens.contacts.ContactPhoneNumber
import com.safeher.app.ui.screens.contacts.ContactsViewModel
import com.safeher.app.ui.screens.contacts.FormDialogState
import com.safeher.app.ui.screens.contacts.ImportedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGuardianRepository : GuardianRepository {
    private val guardians = mutableListOf<Guardian>()
    private val flow = MutableStateFlow<List<Guardian>>(emptyList())
    private val countFlow = MutableStateFlow<Int>(0)
    private var nextId = 1L

    private fun update() {
        flow.value = guardians.toList()
        countFlow.value = guardians.size
    }

    override fun getGuardiansFlow(): Flow<List<Guardian>> = flow
    override fun getGuardianCountFlow(): Flow<Int> = countFlow
    override suspend fun getGuardians(): List<Guardian> = guardians.toList()
    override suspend fun getGuardianCount(): Int = guardians.size

    fun insertDirect(guardian: Guardian) {
        guardians.add(guardian)
        update()
    }

    override suspend fun addGuardian(
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult {
        if (name.isBlank() || name.trim().length < 2) {
            return GuardianOperationResult.ValidationError("name", "Name must be at least 2 characters")
        }
        val norm = GuardianRepositoryImpl.normalizePhoneNumber(phoneNumber)
        val err = GuardianRepositoryImpl.validatePhoneNumber(norm)
        if (err != null) {
            return GuardianOperationResult.ValidationError("phoneNumber", err)
        }
        if (guardians.any { it.phoneNumber == norm }) {
            return GuardianOperationResult.DuplicatePhone("Duplicate phone number")
        }

        val g = Guardian(
            id = nextId++,
            name = name.trim(),
            phoneNumber = norm,
            relationship = relationship,
            isVerified = false
        )
        guardians.add(g)
        update()
        return GuardianOperationResult.Success(g.id)
    }

    override suspend fun updateGuardian(
        id: Long,
        name: String,
        phoneNumber: String,
        relationship: String?
    ): GuardianOperationResult {
        val index = guardians.indexOfFirst { it.id == id }
        if (index == -1) return GuardianOperationResult.Error("Not found")

        if (name.isBlank() || name.trim().length < 2) {
            return GuardianOperationResult.ValidationError("name", "Name must be at least 2 characters")
        }
        val norm = GuardianRepositoryImpl.normalizePhoneNumber(phoneNumber)
        val err = GuardianRepositoryImpl.validatePhoneNumber(norm)
        if (err != null) {
            return GuardianOperationResult.ValidationError("phoneNumber", err)
        }
        if (guardians.any { it.phoneNumber == norm && it.id != id }) {
            return GuardianOperationResult.DuplicatePhone("Duplicate phone number")
        }

        val updated = guardians[index].copy(
            name = name.trim(),
            phoneNumber = norm,
            relationship = relationship
        )
        guardians[index] = updated
        update()
        return GuardianOperationResult.Success(id)
    }

    override suspend fun deleteGuardian(id: Long): Boolean {
        val removed = guardians.removeIf { it.id == id }
        if (removed) update()
        return removed
    }

    override suspend fun getGuardianById(id: Long): Guardian? {
        return guardians.find { it.id == id }
    }

    override suspend fun updateSosEnabled(id: Long, isSosEnabled: Boolean): Boolean {
        val index = guardians.indexOfFirst { it.id == id }
        if (index == -1) return false
        guardians[index] = guardians[index].copy(isSosEnabled = isSosEnabled)
        update()
        return true
    }

    override suspend fun getSosEnabledGuardians(): List<Guardian> {
        return guardians.filter { it.isSosEnabled }
    }

    override fun getSosEnabledGuardiansFlow(): Flow<List<Guardian>> {
        return flow.map { list -> list.filter { it.isSosEnabled } }
    }

    override suspend fun updateBatteryAlertEnabled(id: Long, isBatteryAlertEnabled: Boolean): Boolean {
        val index = guardians.indexOfFirst { it.id == id }
        if (index == -1) return false
        guardians[index] = guardians[index].copy(isBatteryAlertEnabled = isBatteryAlertEnabled)
        update()
        return true
    }

    override suspend fun getBatteryAlertEnabledGuardians(): List<Guardian> {
        return guardians.filter { it.isBatteryAlertEnabled }
    }
}

class ContactsViewModelTest {

    private fun createViewModel(repo: GuardianRepository) = ContactsViewModel(
        repository = repo,
        dispatcher = Dispatchers.Unconfined,
        coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun initialState_isEmpty() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        val state = viewModel.uiState.value
        assertEquals(0, state.guardianCount)
        assertTrue(state.guardians.isEmpty())
        assertFalse(state.isLoading)
        assertEquals(FormDialogState.Closed, state.formState)
    }

    @Test
    fun addGuardian_savesSuccessfullyAndDefaultsToUnverified() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        assertTrue(viewModel.uiState.value.formState is FormDialogState.Open)

        // Using valid Indian number with default country
        viewModel.saveGuardian(name = "Sarah Connor", phone = "9876543210", relationship = "Mother")

        val state = viewModel.uiState.value
        assertEquals(1, state.guardianCount)
        assertEquals("Sarah Connor", state.guardians[0].name)
        assertEquals("+919876543210", state.guardians[0].phoneNumber)
        assertEquals("Mother", state.guardians[0].relationship)
        assertFalse("New guardian must default to unverified", state.guardians[0].isVerified)
        assertEquals(FormDialogState.Closed, state.formState)
    }

    @Test
    fun addGuardian_validationErrorsForEmptyNameOrInvalidPhone() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        // Test empty name
        viewModel.saveGuardian(name = "", phone = "9876543210", relationship = "")
        val nameErrState = viewModel.uiState.value.formState as FormDialogState.Open
        assertNotNull(nameErrState.nameError)

        // Test short phone
        viewModel.saveGuardian(name = "Mom", phone = "123", relationship = "")
        val phoneErrState = viewModel.uiState.value.formState as FormDialogState.Open
        assertNotNull(phoneErrState.phoneError)
    }

    @Test
    fun addGuardian_duplicatePhone_rejected() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.saveGuardian(name = "Mom", phone = "9876543210", relationship = "Family")
        assertEquals(1, viewModel.uiState.value.guardianCount)

        // Try adding duplicate using different formatting of same number (+91 98765 43210)
        viewModel.openAddDialog()
        viewModel.saveGuardian(name = "Sister", phone = "+91 98765 43210", relationship = "Family")
        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertNotNull(formState.phoneError)
        assertEquals(1, viewModel.uiState.value.guardianCount)
    }

    @Test
    fun editGuardian_updatesDetails() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.saveGuardian(name = "Old Name", phone = "9876543210", relationship = "Friend")
        val saved = viewModel.uiState.value.guardians[0]

        viewModel.openEditDialog(saved)
        viewModel.saveGuardian(name = "Updated Name", phone = "9876543210", relationship = "Sister")

        assertEquals("Updated Name", viewModel.uiState.value.guardians[0].name)
        assertEquals("Sister", viewModel.uiState.value.guardians[0].relationship)
    }

    @Test
    fun deleteGuardian_requiresConfirmationAndRemovesRecord() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.saveGuardian(name = "Uncle Bob", phone = "9876543210", relationship = "Family")
        val saved = viewModel.uiState.value.guardians[0]

        viewModel.requestDelete(saved)
        assertEquals(saved, viewModel.uiState.value.deleteConfirmation)

        viewModel.confirmDelete()
        assertNull(viewModel.uiState.value.deleteConfirmation)
        assertEquals(0, viewModel.uiState.value.guardianCount)
    }

    @Test
    fun unsavedChanges_triggersConfirmationBeforeDiscard() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.updateFormField(name = "Incomplete Name", phone = "", relationship = "")

        viewModel.requestCloseForm()
        assertTrue(viewModel.uiState.value.unsavedChangesPending)

        viewModel.confirmDiscard()
        assertFalse(viewModel.uiState.value.unsavedChangesPending)
        assertEquals(FormDialogState.Closed, viewModel.uiState.value.formState)
    }

    // --- Phase 4.1 Contact Import Tests ---

    @Test
    fun contactImport_singleNumber_populatesNameAndPhone() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        val importResult = ContactImportResult.Success(
            ImportedContact(
                displayName = "Alice Wonderland",
                phoneNumbers = listOf(ContactPhoneNumber(number = "9876543210", label = "Mobile"))
            )
        )
        viewModel.onContactImportResult(importResult)

        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("Alice Wonderland", formState.name)
        assertEquals("9876543210", formState.phone)
        assertEquals(0, viewModel.uiState.value.guardianCount) // User must review and save
    }

    @Test
    fun contactImport_multipleNumbers_opensSelectionDialog() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        val importResult = ContactImportResult.Success(
            ImportedContact(
                displayName = "Bob Builder",
                phoneNumbers = listOf(
                    ContactPhoneNumber(number = "9876543210", label = "Mobile"),
                    ContactPhoneNumber(number = "0221234567", label = "Work")
                )
            )
        )
        viewModel.onContactImportResult(importResult)

        val multiDialog = viewModel.uiState.value.multipleNumbersDialog
        assertNotNull(multiDialog)
        assertEquals("Bob Builder", multiDialog?.contactName)
        assertEquals(2, multiDialog?.phoneNumbers?.size)
    }

    @Test
    fun contactImport_selectingNumberFromDialog_populatesFormAndDismisses() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        val importResult = ContactImportResult.Success(
            ImportedContact(
                displayName = "Bob Builder",
                phoneNumbers = listOf(
                    ContactPhoneNumber(number = "9876543210", label = "Mobile"),
                    ContactPhoneNumber(number = "9876500000", label = "Work")
                )
            )
        )
        viewModel.onContactImportResult(importResult)
        viewModel.onSelectMultipleNumberOption("9876500000")

        assertNull(viewModel.uiState.value.multipleNumbersDialog)
        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("Bob Builder", formState.name)
        assertEquals("9876500000", formState.phone)
    }

    @Test
    fun contactImport_noPhoneNumber_showsFriendlyFeedbackAndRetainsName() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        val importResult = ContactImportResult.NoPhoneNumber(displayName = "Charlie Chaplin")
        viewModel.onContactImportResult(importResult)

        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("Charlie Chaplin", formState.name)
        assertEquals("", formState.phone)
        assertNotNull(viewModel.uiState.value.feedbackMessage)
        assertTrue(viewModel.uiState.value.feedbackMessage?.contains("manually") == true)
    }

    @Test
    fun contactImport_error_showsErrorMessageWithoutCrash() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()

        val importResult = ContactImportResult.Error("Could not read contact details")
        viewModel.onContactImportResult(importResult)

        assertEquals("Could not read contact details", viewModel.uiState.value.errorMessage)
    }

    // --- Phase 4.1 Country Selector & International Normalization Tests ---

    @Test
    fun countrySelection_updatesSelectedCountry() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        val uk = CountryRegistry.findByCountryCode("GB")
        viewModel.selectCountry(uk)

        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("GB", formState.selectedCountry.countryCode)
        assertEquals("+44", formState.selectedCountry.dialingCode)
    }

    @Test
    fun pastedInternationalNumber_autoDetectsCountryInViewModel() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        // Pasting UK number while default is India
        viewModel.updateFormField(
            name = "Diana Prince",
            phone = "+44 7911 123456",
            relationship = "Colleague"
        )

        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("GB", formState.selectedCountry.countryCode)
        assertEquals("7911123456", formState.phone)
        assertNotNull(formState.autoDetectedCountryNote)
    }

    // --- Contact Permission & Import Edge Cases Tests ---

    @Test
    fun contactPermission_rationaleShownAndDismissed() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        assertEquals(ContactPermissionDialogState.None, viewModel.uiState.value.contactPermissionDialog)

        viewModel.showContactPermissionRationale()
        assertEquals(ContactPermissionDialogState.Rationale, viewModel.uiState.value.contactPermissionDialog)

        viewModel.dismissContactPermissionDialog()
        assertEquals(ContactPermissionDialogState.None, viewModel.uiState.value.contactPermissionDialog)
    }

    @Test
    fun contactPermission_permanentlyDeniedShownAndDismissed() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.showContactPermissionPermanentlyDenied()
        assertEquals(ContactPermissionDialogState.PermanentlyDenied, viewModel.uiState.value.contactPermissionDialog)

        viewModel.dismissContactPermissionDialog()
        assertEquals(ContactPermissionDialogState.None, viewModel.uiState.value.contactPermissionDialog)
    }

    @Test
    fun contactPermission_granted_triggersPickerAndDismissesDialog() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.showContactPermissionRationale()
        var pickerLaunched = false

        viewModel.onContactPermissionResult(
            isGranted = true,
            isPermanentlyDenied = false,
            onLaunchPicker = { pickerLaunched = true }
        )

        assertTrue("Picker must be launched when permission granted", pickerLaunched)
        assertEquals(ContactPermissionDialogState.None, viewModel.uiState.value.contactPermissionDialog)
    }

    @Test
    fun contactPermission_denied_showsFeedbackMessageAndKeepsManualEntry() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.updateFormField(name = "Manual Name", phone = "9876543210", relationship = "Family")

        var pickerLaunched = false
        viewModel.onContactPermissionResult(
            isGranted = false,
            isPermanentlyDenied = false,
            onLaunchPicker = { pickerLaunched = true }
        )

        assertFalse("Picker must not launch when permission denied", pickerLaunched)
        assertEquals(ContactPermissionDialogState.None, viewModel.uiState.value.contactPermissionDialog)
        assertNotNull(viewModel.uiState.value.feedbackMessage)
        assertTrue(viewModel.uiState.value.feedbackMessage?.contains("denied") == true)

        // Verify form is still open with manual entry intact
        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("Manual Name", formState.name)
        assertEquals("9876543210", formState.phone)
        assertEquals("Family", formState.relationship)
    }

    @Test
    fun contactPermission_permanentlyDenied_showsPermanentlyDeniedDialog() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        var pickerLaunched = false

        viewModel.onContactPermissionResult(
            isGranted = false,
            isPermanentlyDenied = true,
            onLaunchPicker = { pickerLaunched = true }
        )

        assertFalse(pickerLaunched)
        assertEquals(
            ContactPermissionDialogState.PermanentlyDenied,
            viewModel.uiState.value.contactPermissionDialog
        )
        // Form remains open
        assertTrue(viewModel.uiState.value.formState is FormDialogState.Open)
    }

    @Test
    fun contactPicker_cancelled_preservesFormInputsWithoutError() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.updateFormField(name = "Manual Entry", phone = "9876543210", relationship = "Friend")

        viewModel.onContactPickerCancelled()

        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        assertEquals("Manual Entry", formState.name)
        assertEquals("9876543210", formState.phone)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun contactImport_revokedPermissionError_showsErrorMessageWithoutCrash() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        viewModel.onContactImportResult(
            ContactImportResult.Error("Contacts permission required to read contact details.")
        )

        assertEquals(
            "Contacts permission required to read contact details.",
            viewModel.uiState.value.errorMessage
        )
        // Form remains open so user can type manually
        assertTrue(viewModel.uiState.value.formState is FormDialogState.Open)
    }

    @Test
    fun contactImport_doesNotAutoSaveGuardian() {
        val repo = FakeGuardianRepository()
        val viewModel = createViewModel(repo)

        viewModel.openAddDialog()
        val importResult = ContactImportResult.Success(
            ImportedContact(
                displayName = "Jane Doe",
                phoneNumbers = listOf(ContactPhoneNumber(number = "9876543210", label = "Mobile"))
            )
        )
        viewModel.onContactImportResult(importResult)

        // Guardian count must be 0 until explicitly saved
        assertEquals(0, viewModel.uiState.value.guardianCount)

        // Now save explicitly
        val formState = viewModel.uiState.value.formState as FormDialogState.Open
        viewModel.saveGuardian(name = formState.name, phone = formState.phone, relationship = "Sister")

        assertEquals(1, viewModel.uiState.value.guardianCount)
        assertEquals("Jane Doe", viewModel.uiState.value.guardians[0].name)
        assertEquals("+919876543210", viewModel.uiState.value.guardians[0].phoneNumber)
    }
}
