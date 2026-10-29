package com.safeher.app.ui.screens.contacts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.R
import com.safeher.app.data.guardian.Guardian
import com.safeher.app.data.guardian.GuardianRepositoryImpl
import com.safeher.app.data.local.db.SafeHerDatabase
import com.safeher.app.data.phone.CountryInfo
import com.safeher.app.data.phone.CountryRegistry
import com.safeher.app.data.phone.PhoneNumberHelper
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.data.RepositoryProvider
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

class ContactsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = RepositoryProvider.getGuardianRepository(context)
        @Suppress("UNCHECKED_CAST")
        return ContactsViewModel(repo) as T
    }
}

@Composable
fun ContactsScreen(
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = viewModel(
        factory = remember(context) { ContactsViewModelFactory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearFeedbackMessage()
        }
    }

    // System Contact Picker using ActivityResultContracts.PickContact()
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = ContactImportHelper.extractContact(context, uri)
            viewModel.onContactImportResult(result)
        } else {
            viewModel.onContactPickerCancelled()
        }
    }

    // Android Runtime Permission Launcher for READ_CONTACTS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? Activity
        val isPermanentlyDenied = !isGranted && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS)
        } ?: false

        viewModel.onContactPermissionResult(
            isGranted = isGranted,
            isPermanentlyDenied = isPermanentlyDenied,
            onLaunchPicker = { contactPickerLauncher.launch(null) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Screen Header
            ContactsHeader(
                guardianCount = uiState.guardianCount,
                onBackClick = onBackToHomeClick
            )

            // 2. Guardian List or Empty State
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading trusted guardians…",
                        color = SafeHerTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (uiState.guardians.isEmpty()) {
                ContactsEmptyState(
                    onAddClick = { viewModel.openAddDialog() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(
                        items = uiState.guardians,
                        key = { it.id }
                    ) { guardian ->
                        GuardianCard(
                            guardian = guardian,
                            onEditClick = { viewModel.openEditDialog(guardian) },
                            onDeleteClick = { viewModel.requestDelete(guardian) },
                            onVerificationInfoClick = { viewModel.showVerificationInfo(guardian) },
                            onToggleSosClick = { viewModel.toggleSosEnabled(guardian.id, it) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        // 3. Floating Action Button for Adding Guardian
        if (uiState.guardians.isNotEmpty()) {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = SafeHerPurplePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .semantics { contentDescription = "Add new trusted guardian" }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
            }
        }

        // 4. Modals and Dialogs
        val formState = uiState.formState
        if (formState is FormDialogState.Open) {
            AddEditGuardianDialog(
                formState = formState,
                onFieldChange = { name, phone, rel ->
                    viewModel.updateFormField(name, phone, rel)
                },
                onOpenCountryPicker = { viewModel.openCountryPicker() },
                onPickFromContacts = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        contactPickerLauncher.launch(null)
                    } else {
                        viewModel.showContactPermissionRationale()
                    }
                },
                onDismiss = { viewModel.requestCloseForm() },
                onSave = { name, phone, rel ->
                    viewModel.saveGuardian(name, phone, rel)
                }
            )

            if (formState.countryPickerOpen) {
                CountryPickerDialog(
                    onCountrySelected = { viewModel.selectCountry(it) },
                    onDismiss = { viewModel.dismissCountryPicker() }
                )
            }
        }

        // Multiple Numbers Picker Dialog
        uiState.multipleNumbersDialog?.let { multipleDialog ->
            MultipleNumbersSelectionDialog(
                state = multipleDialog,
                onSelectNumber = { viewModel.onSelectMultipleNumberOption(it) },
                onDismiss = { viewModel.dismissMultipleNumbersDialog() }
            )
        }

        // Contact Permission Dialogs
        when (uiState.contactPermissionDialog) {
            is ContactPermissionDialogState.Rationale -> {
                ContactPermissionRationaleDialog(
                    onContinue = {
                        viewModel.dismissContactPermissionDialog()
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onDismiss = { viewModel.dismissContactPermissionDialog() }
                )
            }
            is ContactPermissionDialogState.PermanentlyDenied -> {
                ContactPermissionPermanentlyDeniedDialog(
                    onOpenSettings = {
                        viewModel.dismissContactPermissionDialog()
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    onDismiss = { viewModel.dismissContactPermissionDialog() }
                )
            }
            is ContactPermissionDialogState.None -> { /* No dialog */ }
        }

        // Discard Unsaved Changes Dialog
        if (uiState.unsavedChangesPending) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDiscardDialog() },
                title = { Text("Discard Unsaved Changes?") },
                text = { Text("You have unsaved changes in this guardian entry. Discarding will lose these changes.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDiscard() }) {
                        Text("Discard", color = SafeHerSosCrimson, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDiscardDialog() }) {
                        Text("Keep Editing", color = SafeHerPurplePrimary)
                    }
                },
                containerColor = SafeHerSurfaceWhite,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Delete Confirmation Dialog
        uiState.deleteConfirmation?.let { guardianToDelete ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteDialog() },
                title = { Text("Remove Guardian?") },
                text = {
                    Text(
                        "Are you sure you want to remove ${guardianToDelete.name} from your trusted circle? " +
                            "This contact will no longer be stored locally on your device."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDelete() },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerSosCrimson),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Remove", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                        Text("Cancel", color = SafeHerTextMuted)
                    }
                },
                containerColor = SafeHerSurfaceWhite,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Verification Info Dialog
        uiState.verificationInfo?.let { verifiedTarget ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissVerificationInfo() },
                title = { Text("Guardian Verification Status") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${verifiedTarget.name} (${PhoneNumberHelper.formatForDisplay(verifiedTarget.phoneNumber)}) is currently Unverified.",
                            fontWeight = FontWeight.SemiBold,
                            color = SafeHerTextPrimary
                        )
                        Text(
                            text = "Saving a phone number locally confirms it has been entered, but does NOT verify that this person owns the number or is currently able to receive emergency dispatches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                        Text(
                            text = "To preserve total privacy and avoid unwanted cellular charges, SafeHer does not send SMS or initiate phone calls in this phase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextSecondary
                        )
                        Text(
                            text = "Automated carrier verification and test alerts will be introduced in Phase 7. For now, your guardian is securely saved on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeHerTextMuted
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissVerificationInfo() }) {
                        Text("Got it", color = SafeHerPurplePrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = SafeHerSurfaceWhite,
                shape = RoundedCornerShape(16.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun ContactsHeader(
    guardianCount: Int,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(SafeHerSurfaceWhite, CircleShape)
                    .semantics { contentDescription = "Navigate back to Home" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.contacts_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )
                Text(
                    text = stringResource(R.string.contacts_screen_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy indicator & counter strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SafeHerSurfaceWhite, RoundedCornerShape(12.dp))
                .border(0.5.dp, SafeHerBorderLavender, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.contacts_privacy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = if (guardianCount == 1) "1 Guardian" else "$guardianCount Guardians",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerPurplePrimary
            )
        }
    }
}

@Composable
private fun GuardianCard(
    guardian: Guardian,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onVerificationInfoClick: () -> Unit,
    onToggleSosClick: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Initial Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(SafeHerPurpleContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = guardian.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerPurpleDark,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = guardian.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeHerTextPrimary
                        )

                        if (!guardian.relationship.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(SafeHerPurpleContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = guardian.relationship,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SafeHerPurpleDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = PhoneNumberHelper.formatForDisplay(guardian.phoneNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary
                    )
                }

                // Edit Button
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.semantics { contentDescription = "Edit ${guardian.name}" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.semantics { contentDescription = "Delete ${guardian.name}" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = SafeHerSosCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Verification status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onVerificationInfoClick() }
                    .background(if (guardian.isVerified) SafeHerPurpleContainer else SafeHerPinkContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (guardian.isVerified) Icons.Default.VerifiedUser else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (guardian.isVerified) SafeHerPurpleDark else SafeHerPinkAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (guardian.isVerified) "Verified Contact" else stringResource(R.string.contacts_unverified_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (guardian.isVerified) SafeHerPurpleDark else SafeHerPinkAccent
                    )
                }

                Text(
                    text = "Tap for details",
                    fontSize = 10.sp,
                    color = SafeHerTextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SOS Alert inclusion toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Include in SOS Alerts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SafeHerTextPrimary
                    )
                }

                androidx.compose.material3.Switch(
                    checked = guardian.isSosEnabled,
                    onCheckedChange = { onToggleSosClick(it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SafeHerPurplePrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun ContactsEmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.safeher.app.ui.components.EmptyStatePanel(
            icon = Icons.Default.People,
            title = stringResource(R.string.contacts_empty_title),
            body = stringResource(R.string.contacts_empty_desc),
            actionLabel = stringResource(R.string.contacts_add_first_action),
            onAction = onAddClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SafeHerTextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Contacts stay on device • Zero network upload",
                style = MaterialTheme.typography.labelSmall,
                color = SafeHerTextMuted
            )
        }
    }
}

@Composable
private fun AddEditGuardianDialog(
    formState: FormDialogState.Open,
    onFieldChange: (String, String, String) -> Unit,
    onOpenCountryPicker: () -> Unit,
    onPickFromContacts: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val quickRelationships = listOf("Family", "Friend", "Partner", "Colleague", "Other")
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = if (formState.isEditing) "Edit Trusted Guardian" else "Add Trusted Guardian",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Saved locally. SafeHer never sends automatic notifications.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name Field
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { onFieldChange(it, formState.phone, formState.relationship) },
                    label = { Text("Display Name *") },
                    placeholder = { Text("e.g. Sarah Connor") },
                    isError = formState.nameError != null,
                    supportingText = formState.nameError?.let { { Text(it, color = SafeHerSosCrimson) } },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeHerPurplePrimary,
                        unfocusedBorderColor = SafeHerBorderLavender
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Guardian display name input" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Country Selector & Phone Number Field
                Text(
                    text = "Phone Number *",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Country / Region Selector Button
                    Surface(
                        onClick = onOpenCountryPicker,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SafeHerBorderLavender),
                        color = SafeHerSurfaceWhite,
                        modifier = Modifier
                            .height(56.dp)
                            .semantics {
                                contentDescription = "Selected country ${formState.selectedCountry.name}, dialing code ${formState.selectedCountry.dialingCode}. Tap to change."
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = formState.selectedCountry.flagEmoji,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formState.selectedCountry.dialingCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SafeHerTextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = SafeHerTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // National Phone Number Field
                    OutlinedTextField(
                        value = formState.phone,
                        onValueChange = { onFieldChange(formState.name, it, formState.relationship) },
                        placeholder = { Text(formState.selectedCountry.placeholder) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = formState.phoneError != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafeHerPurplePrimary,
                            unfocusedBorderColor = SafeHerBorderLavender
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Phone number without country code" }
                    )
                }

                // Error or Auto-Detected info note
                if (formState.phoneError != null) {
                    Text(
                        text = formState.phoneError,
                        color = SafeHerSosCrimson,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                } else if (formState.autoDetectedCountryNote != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SafeHerPurplePrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formState.autoDetectedCountryNote,
                            color = SafeHerPurpleDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // System Contact Picker Button
                OutlinedButton(
                    onClick = onPickFromContacts,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeHerPurplePrimary),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 0.5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Import contact from phone contacts" }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import from Phone Contacts",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Relationship Chips
                Text(
                    text = "Relationship (Optional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeHerTextMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickRelationships.take(4).forEach { tag ->
                        val isSelected = formState.relationship.equals(tag, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newRel = if (isSelected) "" else tag
                                onFieldChange(formState.name, formState.phone, newRel)
                            },
                            label = { Text(tag, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SafeHerPurpleContainer,
                                selectedLabelColor = SafeHerPurpleDark
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SafeHerTextMuted)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSave(formState.name, formState.phone, formState.relationship) },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.semantics { contentDescription = if (formState.isEditing) "Save Changes" else "Add Guardian" }
                    ) {
                        Text(if (formState.isEditing) "Save Changes" else "Add Guardian")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryPickerDialog(
    onCountrySelected: (CountryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        CountryRegistry.search(searchQuery)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select Country / Region",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeHerTextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close country picker",
                            tint = SafeHerTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or code (e.g. India, +91)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SafeHerTextMuted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = SafeHerTextMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SafeHerPurplePrimary,
                        unfocusedBorderColor = SafeHerBorderLavender
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredCountries, key = { it.countryCode }) { country ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCountrySelected(country) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                                .semantics {
                                    contentDescription = "Country: ${country.name}, code ${country.dialingCode}"
                                }
                        ) {
                            Text(
                                text = country.flagEmoji,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SafeHerTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = country.dialingCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SafeHerPurplePrimary
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = SafeHerBorderLavender.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MultipleNumbersSelectionDialog(
    state: MultipleNumbersDialogState,
    onSelectNumber: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Select Phone Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafeHerTextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Multiple phone numbers found for ${state.contactName}. Select which number to use for this trusted guardian:",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    state.phoneNumbers.forEach { item ->
                        Surface(
                            onClick = { onSelectNumber(item.number) },
                            shape = RoundedCornerShape(12.dp),
                            color = SafeHerSurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SafeHerBorderLavender),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Phone number ${item.number}, label ${item.label}"
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(SafeHerPurpleContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = SafeHerPurpleDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.number,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SafeHerTextPrimary
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeHerPurplePrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SafeHerTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPermissionRationaleDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.contacts_permission_rationale_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.contacts_permission_rationale_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.contacts_permission_continue),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.contacts_permission_enter_manually),
                    color = SafeHerTextMuted
                )
            }
        },
        containerColor = SafeHerSurfaceWhite,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ContactPermissionPermanentlyDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.contacts_permission_denied_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SafeHerTextPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.contacts_permission_denied_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.contacts_permission_open_settings),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.contacts_permission_enter_manually),
                    color = SafeHerTextMuted
                )
            }
        },
        containerColor = SafeHerSurfaceWhite,
        shape = RoundedCornerShape(16.dp)
    )
}

