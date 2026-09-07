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

class ContactsSyncRepository(val context: Context) {

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
    /**
     * Retrieves all stored enterprise contacts from the device phonebook
     * strictly isolated by ACCOUNT_TYPE = "com.jumhoria.contacts" with fallback to Phone.CONTENT_URI.
     */
    fun getStoredEnterpriseContacts(): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot get stored enterprise contacts: Missing READ_CONTACTS permission.")
            return emptyList()
        }

        val resolver = context.contentResolver
        val contactMap = mutableMapOf<Long, ContactBuilder>()

        // 1. Query RawContacts table under ACCOUNT_TYPE to get corporate contact IDs
        val rawIds = mutableSetOf<Long>()
        val rawUri = ContactsContract.RawContacts.CONTENT_URI
        val rawProj = arrayOf(ContactsContract.RawContacts._ID)
        val rawSel = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.DELETED} = 0"
        val rawArgs = arrayOf(ACCOUNT_TYPE)

        try {
            resolver.query(rawUri, rawProj, rawSel, rawArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                while (cursor.moveToNext()) {
                    rawIds.add(cursor.getLong(idIdx))
                }
            }
            Log.d(TAG, "Found ${rawIds.size} active raw contact IDs for account: $ACCOUNT_TYPE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying raw contacts table", e)
        }

        // 2. Query Data items for these raw contact IDs
        if (rawIds.isNotEmpty()) {
            for (chunk in rawIds.chunked(300)) {
                val dataUri = ContactsContract.Data.CONTENT_URI
                val dataProj = arrayOf(
                    ContactsContract.Data.RAW_CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA4,
                    ContactsContract.Data.DATA5
                )
                val dataSel = "${ContactsContract.Data.RAW_CONTACT_ID} IN (${chunk.joinToString(",")})"

                try {
                    resolver.query(dataUri, dataProj, dataSel, null, null)?.use { cursor ->
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
                                    if (data1.isNotBlank()) {
                                        builder.name = data1
                                    }
                                }
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                                    val clean = data1.replace(Regex("[^0-9+]"), "").trim()
                                    if (clean.isNotBlank() && builder.phoneNumber.isBlank()) {
                                        builder.phoneNumber = clean
                                    }
                                }
                                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                                    if (data1.isNotBlank() && data1 != "Jumhouria Contacts") {
                                        builder.department = data1
                                    } else if (data5.isNotBlank()) {
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
                    Log.e(TAG, "Failed querying data items for raw contact chunk", e)
                }
            }
        }

        // 3. Instant Fallback: If contactMap is empty, query Phone.CONTENT_URI directly
        if (contactMap.isEmpty()) {
            Log.w(TAG, "Enterprise contacts query returned 0, attempting fallback query on Phone.CONTENT_URI...")
            try {
                val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val phoneProj = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                resolver.query(phoneUri, phoneProj, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (cursor.moveToNext()) {
                        val cId = cursor.getString(idIdx).orEmpty()
                        val name = cursor.getString(nameIdx)?.trim().orEmpty()
                        val phone = cursor.getString(phoneIdx)?.trim().orEmpty()
                        if (name.isNotBlank() || phone.isNotBlank()) {
                            val fakeRawId = cId.toLongOrNull() ?: (contactMap.size + 1).toLong()
                            val builder = contactMap.getOrPut(fakeRawId) {
                                ContactBuilder(rawContactId = fakeRawId, syncId = cId, name = name, phoneNumber = phone)
                            }
                            if (builder.name.isBlank()) builder.name = name
                            if (builder.phoneNumber.isBlank()) builder.phoneNumber = phone
                        }
                    }
                }
                Log.d(TAG, "Fallback Phone.CONTENT_URI query returned ${contactMap.size} contacts.")
            } catch (e: Exception) {
                Log.e(TAG, "Fallback Phone.CONTENT_URI query failed", e)
            }
        }

        val resultList = contactMap.values
            .filter { it.name.isNotBlank() || it.phoneNumber.isNotBlank() }
            .map { builder ->
                Contact(
                    contactId = builder.syncId,
                    name = builder.name.trim(),
                    phone = builder.phoneNumber.trim(),
                    email = "",
                    address = builder.department.trim(),
                    notes = builder.note.trim(),
                    department = builder.department.trim(),
                    jobTitle = builder.jobTitle.trim()
                )
            }
            .sortedBy { it.name.lowercase() }

        Log.i(TAG, "getStoredEnterpriseContacts returning ${resultList.size} contacts.")
        return resultList
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
            val matchedRawIds = mutableSetOf<Long>()

            for ((key, sheetContact) in sheetMap) {
                var existing = existingContacts[key]
                if (existing == null && sheetContact.phone.isNotBlank()) {
                    val phoneNorm = normalizePhone(sheetContact.phone)
                    existing = existingContacts.values.firstOrNull {
                        it.rawContactId !in matchedRawIds && normalizePhone(it.phone) == phoneNorm
                    }
                }

                if (existing == null) {
                    // NEW CONTACT -> INSERT
                    Log.i(TAG_DIFF, "[INSERT] Queued new contact: '${sheetContact.name}', Phone: '${sheetContact.phone}'")
                    buildInsertOperations(operations, sheetContact)
                    inserted++
                } else {
                    matchedRawIds.add(existing.rawContactId)

                    // Precise attribute comparison
                    val nameChanged = normalizeText(existing.name) != normalizeText(sheetContact.name)
                    val phoneChanged = normalizePhone(existing.phone) != normalizePhone(sheetContact.phone)
                    val deptChanged = normalizeText(existing.department) != normalizeText(sheetContact.department.ifBlank { sheetContact.address })
                    val notesChanged = normalizeText(existing.notes) != normalizeText(sheetContact.notes)

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
                if (existing.rawContactId !in matchedRawIds && !sheetMap.containsKey(key)) {
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

        // 2. StructuredName (Employee full Arabic name in DISPLAY_NAME & GIVEN_NAME)
        val cleanName = contact.name.trim()
        if (cleanName.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, cleanName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, cleanName)
                    .build()
            )
        }

        // 3. Phone (TYPE_WORK)
        val cleanInsertPhone = contact.phone.trim()
        if (cleanInsertPhone.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, cleanInsertPhone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                    .build()
            )
        }

        // 4. Organization & Job Title (COMPANY = department, TITLE = jobTitle)
        val insertDept = contact.department.ifBlank { contact.address }.trim()
        val insertTitle = contact.jobTitle.trim()
        if (insertDept.isNotBlank() || insertTitle.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, insertDept)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, insertTitle)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }

        // 5. Notes
        val noteContent = contact.notes.trim()
        if (noteContent.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, noteContent)
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
        val cleanName = contact.name.trim()
        if (cleanName.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, cleanName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, cleanName)
                    .build()
            )
        }

        // Re-insert updated Phone
        val cleanUpdatePhone = contact.phone.trim()
        if (cleanUpdatePhone.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, cleanUpdatePhone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                    .build()
            )
        }

        // Re-insert updated Organization (COMPANY = department, TITLE = jobTitle)
        val updateDept = contact.department.ifBlank { contact.address }.trim()
        val updateTitle = contact.jobTitle.trim()
        if (updateDept.isNotBlank() || updateTitle.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, updateDept)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, updateTitle)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build()
            )
        }

        // Re-insert updated Notes
        val noteContent = contact.notes.trim()
        if (noteContent.isNotBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, noteContent)
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
