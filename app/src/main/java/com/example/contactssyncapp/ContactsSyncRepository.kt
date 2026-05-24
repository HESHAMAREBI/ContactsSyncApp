package com.example.contactssyncapp

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import com.example.contactssyncapp.data.Contact
import com.example.contactssyncapp.data.GoogleSheetsRepository

object ContactsSyncRepository {

    private const val ACCOUNT_TYPE = "com.example.contactssyncapp"
    private const val ACCOUNT_NAME = "ContactsSyncApp"

    suspend fun syncContactsFromSheetWithCount(context: Context): Int {
        // First, delete all existing contacts managed by this app to ensure a clean slate.
        deleteAppContacts(context)

        val contacts = GoogleSheetsRepository(context).getContacts()
        contacts.forEach { addContact(context, it) }

        NotificationHelper.showSyncCompleteNotification(context, contacts.size)

        return contacts.size
    }

    fun deleteAppContacts(context: Context): Int {
        val resolver: ContentResolver = context.contentResolver
        val where = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
        val params = arrayOf(ACCOUNT_TYPE)
        // By setting CALLER_IS_SYNCADAPTER to true, the deletion will not be subject to aggregation rules
        // and will permanently delete the raw contacts and all their data.
        val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
        val deletedRows = resolver.delete(uri, where, params)
        android.util.Log.d("ContactSyncDebug", "Deleted $deletedRows raw contacts.")
        return deletedRows
    }

    fun addContact(context: Context, contact: Contact) {
        val resolver: ContentResolver = context.contentResolver
        android.util.Log.d("ContactSyncDebug", "Adding contact: contactId=${contact.contactId}, name=${contact.name}, phone=${contact.phone}, title=${contact.notes}")

        val values = ContentValues().apply {
            put(ContactsContract.RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(ContactsContract.RawContacts.ACCOUNT_NAME, "$ACCOUNT_NAME-${contact.contactId}")
            put(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
            put(ContactsContract.RawContacts.SOURCE_ID, contact.contactId) // إضافة معرف فريد
        }

        val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

        val rawContactUri = resolver.insert(uri, values)
        val rawContactId = rawContactUri?.let { ContentUris.parseId(it) } ?: return
        // Log rawContactId and ACCOUNT_NAME for verification
        android.util.Log.i("ContactSyncDebug", "Created rawContactId=$rawContactId, ACCOUNT_NAME=$ACCOUNT_NAME, SOURCE_ID=${contact.contactId}")

        // Name
        if (contact.name.isNotEmpty()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                }
            )
        }
        // Title (Job)
        if (contact.notes.isNotEmpty()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Organization.TITLE, contact.notes)
                }
            )
        }
        // Phone
        if (contact.phone.isNotEmpty()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone)
                    put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                }
            )
        }
        // Email
        if (contact.email.isNotEmpty()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Email.ADDRESS, contact.email)
                    put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                }
            )
        }
        // Address
        if (contact.address.isNotEmpty()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, contact.address)
                }
            )
        }
    }
}