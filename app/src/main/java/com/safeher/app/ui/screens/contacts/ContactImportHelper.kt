package com.safeher.app.ui.screens.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class ContactPhoneNumber(
    val number: String,
    val label: String
)

data class ImportedContact(
    val displayName: String,
    val phoneNumbers: List<ContactPhoneNumber>
)

sealed interface ContactImportResult {
    data class Success(val contact: ImportedContact) : ContactImportResult
    data class NoPhoneNumber(val displayName: String) : ContactImportResult
    data class Error(val message: String) : ContactImportResult
}

object ContactImportHelper {

    /**
     * Safely queries the ContentResolver for the picked contact's name and available phone numbers
     * using the granted contact Uri and runtime contact permission.
     * Never logs personal contact information.
     */
    fun extractContact(context: Context, contactUri: Uri): ContactImportResult {
        return try {
            val contentResolver = context.contentResolver

            // 1. Retrieve contact ID and display name from the contact URI
            var contactId: Long? = null
            var displayName = ""
            contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIdx != -1) {
                        contactId = cursor.getLong(idIdx)
                    }
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        displayName = cursor.getString(nameIdx).orEmpty().trim()
                    }
                }
            }

            // 2. Query phone numbers from the contact's Data subpath
            val phoneNumbers = mutableListOf<ContactPhoneNumber>()
            val dataUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)

            contentResolver.query(
                dataUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                null
            )?.use { phoneCursor ->
                val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (phoneCursor.moveToNext()) {
                    val rawNumber = if (numIdx != -1) phoneCursor.getString(numIdx).orEmpty().trim() else ""
                    if (rawNumber.isNotEmpty()) {
                        val type = if (typeIdx != -1) phoneCursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                        val customLabel = if (labelIdx != -1) phoneCursor.getString(labelIdx) else null
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            type,
                            customLabel
                        ).toString()

                        // Avoid adding exact duplicate numbers for the same contact
                        if (phoneNumbers.none { it.number == rawNumber }) {
                            phoneNumbers.add(ContactPhoneNumber(number = rawNumber, label = label))
                        }
                    }
                }
            }

            // Fallback for OEM device variants: query Phone.CONTENT_URI directly by CONTACT_ID
            if (phoneNumbers.isEmpty() && contactId != null) {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { fallbackCursor ->
                    val numIdx = fallbackCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = fallbackCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIdx = fallbackCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                    while (fallbackCursor.moveToNext()) {
                        val rawNumber = if (numIdx != -1) fallbackCursor.getString(numIdx).orEmpty().trim() else ""
                        if (rawNumber.isNotEmpty()) {
                            val type = if (typeIdx != -1) fallbackCursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                            val customLabel = if (labelIdx != -1) fallbackCursor.getString(labelIdx) else null
                            val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                type,
                                customLabel
                            ).toString()

                            if (phoneNumbers.none { it.number == rawNumber }) {
                                phoneNumbers.add(ContactPhoneNumber(number = rawNumber, label = label))
                            }
                        }
                    }
                }
            }

            if (phoneNumbers.isEmpty()) {
                ContactImportResult.NoPhoneNumber(displayName)
            } else {
                ContactImportResult.Success(
                    ImportedContact(
                        displayName = displayName,
                        phoneNumbers = phoneNumbers
                    )
                )
            }
        } catch (_: SecurityException) {
            ContactImportResult.Error("Contacts permission required to read contact details.")
        } catch (_: Exception) {
            ContactImportResult.Error("Unable to read contact details from device.")
        }
    }
}
