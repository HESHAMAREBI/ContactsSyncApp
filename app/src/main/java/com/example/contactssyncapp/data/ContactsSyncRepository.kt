package com.example.contactssyncapp.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.contactssyncapp.NotificationHelper

data class SyncResult(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val unchanged: Int = 0
) {
    val totalActive: Int get() = inserted + updated + unchanged
}

private data class ExistingPhoneContact(
    val rawContactId: Long,
    val syncId: String,
    var name: String = "",
    var department: String = "",
    var phone: String = "",
    var notes: String = ""
)

class ContactsSyncRepository(private val context: Context) {

    companion object {
        private const val TAG = "ContactsSync"
        private const val TAG_DIFF = "DiffEngine"
        const val ACCOUNT_TYPE = "com.jumhoria.contacts"
        private const val BATCH_OPERATION_LIMIT = 100

        /**
         * Deletes all corporate contacts previously created by this application.
         * Isolates deletion strictly to ACCOUNT_TYPE = "com.jumhoria.contacts".
         */
        fun deleteAppContacts(context: Context): Int {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot delete contacts: Missing WRITE_CONTACTS permission.")
                return 0
            }

            val resolver: ContentResolver = context.contentResolver
            val where = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
            val params = arrayOf(ACCOUNT_TYPE)

            val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build()

            return try {
                val deletedRows = resolver.delete(uri, where, params)
                Log.i(TAG, "Successfully deleted $deletedRows corporate contacts for account: $ACCOUNT_TYPE")
                deletedRows
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete contacts for account: $ACCOUNT_TYPE", e)
                0
            }
        }

        /**
         * Orchestrates full smart diffing sync from Google Sheets:
         * 1. Fetches fresh data from Google Sheets (bypassing any empty cache).
         * 2. When Phone DB is empty (count = 0), forces a direct full batch insert.
         * 3. Sends notification with active contacts count.
         */
        suspend fun syncContactsFromSheetWithCount(context: Context): Int {
            Log.i(TAG, "Manual/Direct sync triggered: Fetching fresh contacts from Google Sheets...")
            val sheetsRepo = GoogleSheetsRepository(context)
            val sheetContacts = sheetsRepo.getContacts()

            Log.i(TAG, "Total contacts fetched from Sheets: ${sheetContacts.size}")
            val repository = ContactsSyncRepository(context)
            val syncResult = repository.syncContactsWithDiff(sheetContacts)

            NotificationHelper.showSyncCompleteNotification(context, syncResult.totalActive)
            return syncResult.totalActive
        }
    }

    data class ContactBuilder(
        var rawContactId: Long = 0L,
        var syncId: String = "",
        var name: String = "",
        var phoneNumber: String = "",
        var department: String = "",
        var jobTitle: String = "",
        var note: String = ""
    )

    /**
     * Retrieves all stored enterprise contacts from the device phonebook
     * strictly isolated by ACCOUNT_TYPE = "com.jumhoria.contacts".
     */
    fun getStoredEnterpriseContacts(): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot get stored enterprise contacts: Missing READ_CONTACTS permission.")
            return emptyList()
        }

        val resolver = context.contentResolver
        val contactMap = mutableMapOf<Long, ContactBuilder>()

        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.DELETED} = 0"
        val selectionArgs = arrayOf(ACCOUNT_TYPE)

        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val rawIdIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
                val data5Idx = cursor.getColumnIndex(ContactsContract.Data.DATA5)

                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(rawIdIdx)
                    val mime = cursor.getString(mimeIdx).orEmpty()
                    val data1 = cursor.getString(data1Idx)?.trim().orEmpty()
                    val data4 = cursor.getString(data4Idx)?.trim().orEmpty()
                    val data5 = cursor.getString(data5Idx)?.trim().orEmpty()

                    val builder = contactMap.getOrPut(rawId) {
                        ContactBuilder(rawContactId = rawId, syncId = rawId.toString())
                    }

                    when (mime) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) builder.name = data1
                        }
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val clean = PhoneUtils.extractCleanPhone(data1)
                            if (clean.isNotBlank() && builder.phoneNumber.isBlank()) {
                                builder.phoneNumber = clean
                            } else if (builder.phoneNumber.isBlank()) {
                                builder.phoneNumber = data1
                            }
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && data1 != "Jumhouria Contacts" && builder.department.isBlank()) {
                                builder.department = data1
                            }
                            if (data5.isNotBlank() && builder.department.isBlank()) {
                                builder.department = data5
                            }
                            if (data4.isNotBlank()) {
                                builder.jobTitle = data4
                            }
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) builder.note = data1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying stored enterprise contacts", e)
        }

        return contactMap.values
            .mapNotNull { builder ->
                val name = builder.name.trim()
                if (name.isBlank()) {
                    null
                } else {
                    val effectiveDept = if (builder.department.isNotBlank()) builder.department else builder.jobTitle
                    val effectiveTitle = if (builder.jobTitle.isNotBlank() && builder.jobTitle != builder.department) builder.jobTitle else ""

                    Contact(
                        contactId = builder.syncId,
                        name = name,
                        phone = builder.phoneNumber.trim(),
                        email = "",
                        address = effectiveDept,
                        notes = builder.note,
                        department = effectiveDept,
                        jobTitle = effectiveTitle
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Backward-compatible sync entry point returning the number of active synced contacts.
     */
    fun syncContacts(contacts: List<Contact>): Int {
        return syncContactsWithDiff(contacts).totalActive
    }

    /**
     * Core Smart Diffing & Batch Insertion Engine:
     * - Queries existing phone contacts under ACCOUNT_TYPE = "com.jumhoria.contacts"
     * - Matches by Normalized Name (fallback to Phone)
     * - Accurately categorizes into Insert, Update, Delete, or Unchanged
     */
    fun syncContactsWithDiff(sheetContacts: List<Contact>): SyncResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot sync contacts: Missing READ_CONTACTS or WRITE_CONTACTS permission.")
            return SyncResult(inserted = 0, updated = 0, deleted = 0, unchanged = 0)
        }

        val resolver: ContentResolver = context.contentResolver

        var inserted = 0
        var updated = 0
        var deleted = 0
        var unchanged = 0

        try {
            Log.i(TAG, "Total contacts fetched from Sheets: ${sheetContacts.size}")
            val existingContacts = queryExistingPhoneContacts(resolver)
            val phoneCount = existingContacts.size
            Log.i(TAG, "Total corporate contacts found in Phone DB: $phoneCount")

            if (sheetContacts.isEmpty()) {
                Log.w(TAG, "No contacts received from Google Sheets. Aborting sync to prevent accidental deletion.")
                return SyncResult(inserted = 0, updated = 0, deleted = 0, unchanged = phoneCount)
            }

            val operations = ArrayList<ContentProviderOperation>()

            // Fast path: When phone directory is completely empty, force full batch insert
            if (phoneCount == 0) {
                Log.i(TAG, "Phone DB is empty (count = 0). Forcing direct batch insert for all ${sheetContacts.size} contacts...")
                for (contact in sheetContacts) {
                    buildInsertOperations(operations, contact)
                    inserted++

                    if (operations.size >= BATCH_OPERATION_LIMIT) {
                        executeBatch(resolver, operations)
                    }
                }

                if (operations.isNotEmpty()) {
                    executeBatch(resolver, operations)
                }

                Log.i(TAG_DIFF, "To Insert: $inserted, To Update: 0, To Delete: 0, Unchanged: 0")
                Log.i(TAG, "Sync summary: Inserted=$inserted, Updated=0, Deleted=0, Unchanged=0")
                return SyncResult(inserted = inserted, updated = 0, deleted = 0, unchanged = 0)
            }

            // Standard Smart Diff path: Build lookup map by Normalized Key (Name -> Phone fallback)
            val sheetMap = LinkedHashMap<String, Contact>()
            for (contact in sheetContacts) {
                val key = normalizeKey(contact.name, contact.phone)
                if (key.isNotEmpty()) {
                    sheetMap[key] = contact
                }
            }

            // 1. Process Sheet contacts -> Determine INSERTS, UPDATES, or UNCHANGED
            for ((key, sheetContact) in sheetMap) {
                val existing = existingContacts[key]

                if (existing == null) {
                    // NEW CONTACT -> INSERT
                    Log.i(TAG_DIFF, "[INSERT] Queued new contact: '${sheetContact.name}', Phone: '${sheetContact.phone}'")
                    buildInsertOperations(operations, sheetContact)
                    inserted++
                } else {
                    // Precise attribute comparison
                    val nameChanged = normalizeText(existing.name) != normalizeText(sheetContact.name)
                    val phoneChanged = normalizePhone(existing.phone) != normalizePhone(sheetContact.phone)
                    val deptChanged = normalizeText(existing.department) != normalizeText(sheetContact.address)
                    val effectiveSheetNotes = if (sheetContact.notes.trim() == sheetContact.address.trim()) "" else sheetContact.notes.trim()
                    val notesChanged = normalizeText(existing.notes) != normalizeText(effectiveSheetNotes)

                    if (nameChanged || phoneChanged || deptChanged || notesChanged) {
                        // MODIFIED CONTACT -> UPDATE
                        Log.i(TAG_DIFF, "[UPDATE] Queued updated contact: '${sheetContact.name}', nameChanged=$nameChanged, phoneChanged=$phoneChanged, deptChanged=$deptChanged, notesChanged=$notesChanged")
                        buildUpdateOperations(operations, existing.rawContactId, sheetContact)
                        updated++
                    } else {
                        // UNCHANGED -> DO NOT QUEUE OPERATIONS
                        unchanged++
                    }
                }

                if (operations.size >= BATCH_OPERATION_LIMIT) {
                    executeBatch(resolver, operations)
                }
            }

            // 2. Process DELETED contacts (Present on phone under our account but missing from Sheet)
            for ((key, existing) in existingContacts) {
                if (!sheetMap.containsKey(key)) {
                    Log.i(TAG_DIFF, "[DELETE] Queued deleted contact: '${existing.name}', Phone: '${existing.phone}', rawContactId=${existing.rawContactId}")
                    buildDeleteOperation(operations, existing.rawContactId)
                    deleted++

                    if (operations.size >= BATCH_OPERATION_LIMIT) {
                        executeBatch(resolver, operations)
                    }
                }
            }

            // Flush remaining operations
            if (operations.isNotEmpty()) {
                executeBatch(resolver, operations)
            }

            Log.i(TAG_DIFF, "To Insert: $inserted, To Update: $updated, To Delete: $deleted, Unchanged: $unchanged")
            Log.i(TAG, "Sync summary: Inserted=$inserted, Updated=$updated, Deleted=$deleted, Unchanged=$unchanged")
            return SyncResult(inserted = inserted, updated = updated, deleted = deleted, unchanged = unchanged)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing sync with diff", e)
            return SyncResult(inserted = inserted, updated = updated, deleted = deleted, unchanged = unchanged)
        }
    }

    private fun queryExistingPhoneContacts(resolver: ContentResolver): Map<String, ExistingPhoneContact> {
        val contactsMap = LinkedHashMap<String, ExistingPhoneContact>()
        val rawMap = LinkedHashMap<Long, ExistingPhoneContact>()

        val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

        val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.DELETED} = 0"
        val selectionArgs = arrayOf(ACCOUNT_TYPE)

        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.SYNC1,
            ContactsContract.RawContacts.SOURCE_ID
        )

        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                val sync1Idx = cursor.getColumnIndex(ContactsContract.RawContacts.SYNC1)
                val sourceIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)

                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(idIdx)
                    val sync1 = cursor.getString(sync1Idx)?.trim().orEmpty()
                    val sourceId = cursor.getString(sourceIdIdx)?.trim().orEmpty()
                    val key = sync1.ifEmpty { sourceId }

                    rawMap[rawId] = ExistingPhoneContact(rawContactId = rawId, syncId = key)
                }
            }

            if (rawMap.isNotEmpty()) {
                val dataUri = ContactsContract.Data.CONTENT_URI
                val dataProjection = arrayOf(
                    ContactsContract.Data.RAW_CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA4,
                    ContactsContract.Data.DATA5
                )
                val dataSelection = "${ContactsContract.Data.RAW_CONTACT_ID} IN (${rawMap.keys.joinToString(",")})"

                resolver.query(dataUri, dataProjection, dataSelection, null, null)?.use { dataCursor ->
                    val rawIdIdx = dataCursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                    val mimeIdx = dataCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val data1Idx = dataCursor.getColumnIndex(ContactsContract.Data.DATA1)
                    val data4Idx = dataCursor.getColumnIndex(ContactsContract.Data.DATA4)
                    val data5Idx = dataCursor.getColumnIndex(ContactsContract.Data.DATA5)

                    while (dataCursor.moveToNext()) {
                        val rawId = dataCursor.getLong(rawIdIdx)
                        val mime = dataCursor.getString(mimeIdx).orEmpty()
                        val data1 = dataCursor.getString(data1Idx)?.trim().orEmpty()
                        val data4 = dataCursor.getString(data4Idx)?.trim().orEmpty()
                        val data5 = dataCursor.getString(data5Idx)?.trim().orEmpty()

                        val contact = rawMap[rawId] ?: continue
                        when (mime) {
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                                contact.name = data1
                            }
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                                contact.phone = data1
                            }
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                                // Organization: DATA5 is DEPARTMENT, DATA4 is TITLE, DATA1 is COMPANY
                                contact.department = if (data5.isNotBlank()) data5 else if (data4.isNotBlank()) data4 else data1
                            }
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                                contact.notes = data1
                            }
                        }
                    }
                }
            }

            for (c in rawMap.values) {
                val key = normalizeKey(c.name, c.phone)
                if (key.isNotEmpty()) {
                    contactsMap[key] = c
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying existing phone contacts", e)
        }

        return contactsMap
    }

    private fun buildInsertOperations(operations: ArrayList<ContentProviderOperation>, contact: Contact) {
        val rawIndex = operations.size
        Log.d(TAG, "Building insert for: ${contact.name} -> ${contact.phone} (Dept: ${contact.address})")

        // 1. RawContact insert
        val accountName = AppConfigManager.getAccountName(context)
        operations.add(
            ContentProviderOperation.newInsert(
                ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                    .build()
            )
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .withValue(ContactsContract.RawContacts.SYNC1, contact.contactId)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, contact.contactId)
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
                .build()
        )

        // 2. StructuredName
        if (contact.name.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name.trim())
                    .build()
            )
        }

        // 3. Phone (TYPE_WORK)
        if (contact.phone.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone.trim())
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                    .build()
            )
        }

        // 4. Organization / Department (Column C)
        if (contact.address.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, "Jumhouria Contacts")
                    .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, contact.address.trim())
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.address.trim())
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }

        // 5. Notes / Extra (Column E)
        if (contact.notes.isNotBlank() && contact.notes.trim() != contact.address.trim()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.notes.trim())
                    .build()
            )
        }
    }

    private fun buildUpdateOperations(
        operations: ArrayList<ContentProviderOperation>,
        rawContactId: Long,
        contact: Contact
    ) {
        Log.d(TAG, "Building update for: ${contact.name} -> ${contact.phone}")

        // Clear existing data items for this raw contact to avoid duplicate entries
        operations.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawContactId.toString()))
                .build()
        )

        // Re-insert updated StructuredName
        if (contact.name.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name.trim())
                    .build()
            )
        }

        // Re-insert updated Phone
        if (contact.phone.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone.trim())
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                    .build()
            )
        }

        // Re-insert updated Organization (Department / Branch)
        if (contact.address.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, "Jumhouria Contacts")
                    .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, contact.address.trim())
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.address.trim())
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }

        // Re-insert updated Notes
        if (contact.notes.isNotBlank() && contact.notes.trim() != contact.address.trim()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.notes.trim())
                    .build()
            )
        }
    }

    private fun buildDeleteOperation(operations: ArrayList<ContentProviderOperation>, rawContactId: Long) {
        Log.d(TAG, "Building delete for rawContactId=$rawContactId")
        operations.add(
            ContentProviderOperation.newDelete(
                ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                    .build()
            )
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
                .build()
        )
    }

    private fun executeBatch(resolver: ContentResolver, operations: ArrayList<ContentProviderOperation>) {
        if (operations.isEmpty()) return
        Log.i(TAG, "Executing batch for ${operations.size} operations...")
        try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            Log.i(TAG, "Successfully applied batch with ${operations.size} operations (${results.size} provider results).")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: ContentResolver.applyBatch FAILED for batch of ${operations.size} operations: ${e.message}", e)
        } finally {
            operations.clear()
        }
    }

    private fun normalizeKey(name: String?, phone: String?): String {
        val cleanName = normalizeName(name)
        if (cleanName.isNotEmpty()) return cleanName
        return normalizePhone(phone)
    }

    private fun normalizeName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.trim()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun normalizeText(str: String?): String {
        if (str == null) return ""
        return str.trim()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val digits = phone.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("00218") -> digits.removePrefix("00218").removePrefix("0")
            digits.startsWith("218") -> digits.removePrefix("218").removePrefix("0")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }
}
