package com.safeher.app.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianOperationResult
import com.safeher.app.data.guardian.GuardianRepository
import com.safeher.app.data.phone.CountryInfo
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneParseResult
import com.safeher.app.data.phone.PhoneNumberHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FormDialogState {
    data object Closed : FormDialogState
    data class Open(
        val isEditing: Boolean = false,
        val guardianId: Long = 0,
        val name: String = "",
        val phone: String = "",
        val selectedCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY,
        val relationship: String = "",
        val initialName: String = "",
        val initialPhone: String = "",
        val initialCountry: CountryInfo = CountryRegistry.DEFAULT_COUNTRY,
        val initialRelationship: String = "",
        val nameError: String? = null,
        val phoneError: String? = null,
        val countryPickerOpen: Boolean = false,
        val autoDetectedCountryNote: String? = null
    ) : FormDialogState
}

sealed interface ContactPermissionDialogState {
    data object None : ContactPermissionDialogState
    data object Rationale : ContactPermissionDialogState
    data object PermanentlyDenied : ContactPermissionDialogState
}

data class MultipleNumbersDialogState(
    val contactName: String,
    val phoneNumbers: List<ContactPhoneNumber>
)

data class ContactsUiState(
    val guardians: List<Guardian> = emptyList(),
    val guardianCount: Int = 0,
    val isLoading: Boolean = true,
    val formState: FormDialogState = FormDialogState.Closed,
    val multipleNumbersDialog: MultipleNumbersDialogState? = null,
    val contactPermissionDialog: ContactPermissionDialogState = ContactPermissionDialogState.None,
    val deleteConfirmation: Guardian? = null,
    val verificationInfo: Guardian? = null,
    val unsavedChangesPending: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class ContactsViewModel(
    private val repository: GuardianRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadGuardians()
    }

    private fun loadGuardians() {
        repository.getGuardiansFlow()
            .onEach { list ->
                _uiState.update {
                    it.copy(
                        guardians = list,
                        guardianCount = list.size,
                        isLoading = false
                    )
                }
            }
            .launchIn(scope)
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                formState = FormDialogState.Open(
                    isEditing = false,
                    guardianId = 0,
                    name = "",
                    phone = "",
                    selectedCountry = CountryRegistry.DEFAULT_COUNTRY,
                    relationship = "",
                    initialName = "",
                    initialPhone = "",
                    initialCountry = CountryRegistry.DEFAULT_COUNTRY,
                    initialRelationship = ""
                )
            )
        }
    }

    fun openEditDialog(guardian: Guardian) {
        val (country, national) = PhoneNumberHelper.extractDetails(guardian.phoneNumber)
        _uiState.update {
            it.copy(
                formState = FormDialogState.Open(
                    isEditing = true,
                    guardianId = guardian.id,
                    name = guardian.name,
                    phone = national,
                    selectedCountry = country,
                    relationship = guardian.relationship.orEmpty(),
                    initialName = guardian.name,
                    initialPhone = national,
                    initialCountry = country,
                    initialRelationship = guardian.relationship.orEmpty()
                )
            )
        }
    }

    fun updateFormField(
        name: String,
        phone: String,
        relationship: String,
        country: CountryInfo? = null
    ) {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        val targetCountry = country ?: current.selectedCountry

        // Check if the user pasted an international number starting with +
        if (phone.startsWith("+")) {
            val parseResult = PhoneNumberHelper.parseAndValidate(phone, targetCountry)
            if (parseResult is PhoneParseResult.Valid) {
                _uiState.update {
                    it.copy(
                        formState = current.copy(
                            name = name,
                            phone = parseResult.national,
                            selectedCountry = parseResult.country,
                            relationship = relationship,
                            nameError = if (name.isNotBlank()) null else current.nameError,
                            phoneError = null,
                            autoDetectedCountryNote = if (parseResult.wasAutoDetected) {
                                "Detected ${parseResult.country.name} (${parseResult.country.dialingCode})"
                            } else null
                        )
                    )
                }
                return
            }
        }

        // Check if the user pasted a number with country code without +
        val digitsOnly = phone.filter { it.isDigit() }
        val numericDialing = targetCountry.dialingCode.removePrefix("+")
        if (digitsOnly.startsWith(numericDialing) && digitsOnly.length > numericDialing.length + 6) {
            val parseResult = PhoneNumberHelper.parseAndValidate("+$digitsOnly", targetCountry)
            if (parseResult is PhoneParseResult.Valid) {
                _uiState.update {
                    it.copy(
                        formState = current.copy(
                            name = name,
                            phone = parseResult.national,
                            selectedCountry = parseResult.country,
                            relationship = relationship,
                            nameError = if (name.isNotBlank()) null else current.nameError,
                            phoneError = null,
                            autoDetectedCountryNote = null
                        )
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                formState = current.copy(
                    name = name,
                    phone = phone,
                    selectedCountry = targetCountry,
                    relationship = relationship,
                    nameError = if (name.isNotBlank()) null else current.nameError,
                    phoneError = if (phone.isNotBlank()) null else current.phoneError,
                    autoDetectedCountryNote = null
                )
            )
        }
    }

    fun openCountryPicker() {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        _uiState.update { it.copy(formState = current.copy(countryPickerOpen = true)) }
    }

    fun dismissCountryPicker() {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        _uiState.update { it.copy(formState = current.copy(countryPickerOpen = false)) }
    }

    fun selectCountry(country: CountryInfo) {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        _uiState.update {
            it.copy(
                formState = current.copy(
                    selectedCountry = country,
                    countryPickerOpen = false,
                    phoneError = null,
                    autoDetectedCountryNote = null
                )
            )
        }
    }

    // --- Contact Permission Handling ---

    fun showContactPermissionRationale() {
        _uiState.update { it.copy(contactPermissionDialog = ContactPermissionDialogState.Rationale) }
    }

    fun showContactPermissionPermanentlyDenied() {
        _uiState.update { it.copy(contactPermissionDialog = ContactPermissionDialogState.PermanentlyDenied) }
    }

    fun dismissContactPermissionDialog() {
        _uiState.update { it.copy(contactPermissionDialog = ContactPermissionDialogState.None) }
    }

    fun onContactPermissionResult(
        isGranted: Boolean,
        isPermanentlyDenied: Boolean = false,
        onLaunchPicker: () -> Unit
    ) {
        if (isGranted) {
            _uiState.update { it.copy(contactPermissionDialog = ContactPermissionDialogState.None) }
            onLaunchPicker()
        } else {
            if (isPermanentlyDenied) {
                _uiState.update { it.copy(contactPermissionDialog = ContactPermissionDialogState.PermanentlyDenied) }
            } else {
                _uiState.update {
                    it.copy(
                        contactPermissionDialog = ContactPermissionDialogState.None,
                        feedbackMessage = "Contacts permission was denied. You can enter your guardian's details manually."
                    )
                }
            }
        }
    }

    fun onContactPickerCancelled() {
        // Preserves current form inputs without crashing or clearing
    }

    // --- Contact Import Handling ---

    fun onContactImportResult(result: ContactImportResult) {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        when (result) {
            is ContactImportResult.Success -> {
                val contact = result.contact
                if (contact.phoneNumbers.size == 1) {
                    applyImportedContact(
                        name = contact.displayName,
                        rawPhone = contact.phoneNumbers[0].number
                    )
                } else if (contact.phoneNumbers.size > 1) {
                    _uiState.update {
                        it.copy(
                            multipleNumbersDialog = MultipleNumbersDialogState(
                                contactName = contact.displayName,
                                phoneNumbers = contact.phoneNumbers
                            )
                        )
                    }
                }
            }
            is ContactImportResult.NoPhoneNumber -> {
                val updatedName = if (result.displayName.isNotBlank()) result.displayName else current.name
                _uiState.update {
                    it.copy(
                        formState = current.copy(name = updatedName, phone = ""),
                        feedbackMessage = "This contact does not have a phone number. Please enter the number manually."
                    )
                }
            }
            is ContactImportResult.Error -> {
                _uiState.update {
                    it.copy(errorMessage = result.message)
                }
            }
        }
    }

    fun onSelectMultipleNumberOption(number: String) {
        val dialog = _uiState.value.multipleNumbersDialog ?: return
        applyImportedContact(name = dialog.contactName, rawPhone = number)
        _uiState.update { it.copy(multipleNumbersDialog = null) }
    }

    fun dismissMultipleNumbersDialog() {
        _uiState.update { it.copy(multipleNumbersDialog = null) }
    }

    private fun applyImportedContact(name: String, rawPhone: String) {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        val parseResult = PhoneNumberHelper.parseAndValidate(rawPhone, current.selectedCountry)

        val (targetCountry, nationalPhone, note) = when (parseResult) {
            is PhoneParseResult.Valid -> {
                Triple(
                    parseResult.country,
                    parseResult.national,
                    if (parseResult.wasAutoDetected) "Detected ${parseResult.country.name} (${parseResult.country.dialingCode})" else null
                )
            }
            is PhoneParseResult.Invalid -> {
                // Keep raw digits if unparseable, retaining current country
                Triple(current.selectedCountry, rawPhone.filter { it.isDigit() }, null)
            }
        }

        val updatedName = if (name.isNotBlank()) name else current.name

        _uiState.update {
            it.copy(
                formState = current.copy(
                    name = updatedName,
                    phone = nationalPhone,
                    selectedCountry = targetCountry,
                    nameError = null,
                    phoneError = null,
                    autoDetectedCountryNote = note
                )
            )
        }
    }

    // --- Form Close and Confirmation ---

    fun requestCloseForm() {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        val hasChanges = current.name != current.initialName ||
            current.phone != current.initialPhone ||
            current.relationship != current.initialRelationship ||
            current.selectedCountry.countryCode != current.initialCountry.countryCode

        if (hasChanges && (current.name.isNotBlank() || current.phone.isNotBlank())) {
            _uiState.update { it.copy(unsavedChangesPending = true) }
        } else {
            closeFormImmediate()
        }
    }

    fun confirmDiscard() {
        _uiState.update {
            it.copy(
                formState = FormDialogState.Closed,
                unsavedChangesPending = false,
                multipleNumbersDialog = null,
                contactPermissionDialog = ContactPermissionDialogState.None
            )
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(unsavedChangesPending = false) }
    }

    fun closeFormImmediate() {
        _uiState.update {
            it.copy(
                formState = FormDialogState.Closed,
                unsavedChangesPending = false,
                multipleNumbersDialog = null,
                contactPermissionDialog = ContactPermissionDialogState.None
            )
        }
    }

    fun saveGuardian(name: String, phone: String, relationship: String) {
        val current = _uiState.value.formState as? FormDialogState.Open ?: return
        val trimmedName = name.trim()

        if (trimmedName.isEmpty() || trimmedName.length < 2) {
            _uiState.update {
                it.copy(
                    formState = current.copy(
                        nameError = "Name must be at least 2 characters"
                    )
                )
            }
            return
        }

        val parseResult = PhoneNumberHelper.parseAndValidate(phone, current.selectedCountry)
        val canonicalPhone = when (parseResult) {
            is PhoneParseResult.Valid -> parseResult.e164
            is PhoneParseResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        formState = current.copy(
                            phoneError = parseResult.reason
                        )
                    )
                }
                return
            }
        }

        scope.launch(dispatcher) {
            val result = if (current.isEditing) {
                repository.updateGuardian(
                    id = current.guardianId,
                    name = trimmedName,
                    phoneNumber = canonicalPhone,
                    relationship = relationship
                )
            } else {
                repository.addGuardian(
                    name = trimmedName,
                    phoneNumber = canonicalPhone,
                    relationship = relationship
                )
            }

            when (result) {
                is GuardianOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            formState = FormDialogState.Closed,
                            unsavedChangesPending = false,
                            feedbackMessage = if (current.isEditing) {
                                "Guardian details updated locally."
                            } else {
                                "New guardian added to local circle."
                            }
                        )
                    }
                }
                is GuardianOperationResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            formState = current.copy(
                                nameError = if (result.field == "name") result.message else null,
                                phoneError = if (result.field == "phoneNumber") result.message else null
                            )
                        )
                    }
                }
                is GuardianOperationResult.DuplicatePhone -> {
                    _uiState.update {
                        it.copy(
                            formState = current.copy(
                                phoneError = result.message
                            )
                        )
                    }
                }
                is GuardianOperationResult.Error -> {
                    _uiState.update {
                        it.copy(errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun requestDelete(guardian: Guardian) {
        _uiState.update { it.copy(deleteConfirmation = guardian) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteConfirmation = null) }
    }

    fun confirmDelete() {
        val guardian = _uiState.value.deleteConfirmation ?: return
        scope.launch(dispatcher) {
            val success = repository.deleteGuardian(guardian.id)
            _uiState.update {
                it.copy(
                    deleteConfirmation = null,
                    feedbackMessage = if (success) {
                        "${guardian.name} removed from trusted circle."
                    } else {
                        "Failed to delete guardian."
                    }
                )
            }
        }
    }

    fun showVerificationInfo(guardian: Guardian) {
        _uiState.update { it.copy(verificationInfo = guardian) }
    }

    fun dismissVerificationInfo() {
        _uiState.update { it.copy(verificationInfo = null) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }
}
