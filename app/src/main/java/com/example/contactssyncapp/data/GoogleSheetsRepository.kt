package com.example.contactssyncapp.data

import android.content.Context
import android.util.Log
import com.example.contactssyncapp.R
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.InputStream

class GoogleSheetsRepository(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSheetsRepo"
        private const val APPLICATION_NAME = "ContactsSyncApp"
    }

    fun getContacts(
        spreadsheetId: String = AppConfigManager.getSpreadsheetId(context),
        range: String = AppConfigManager.getSheetRange(context)
    ): List<Contact> {
        return try {
            Log.d(TAG, "Initializing Google Sheets service and loading credentials...")
            val credential = getCredentials()
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val sheets = Sheets.Builder(transport, jsonFactory, HttpCredentialsAdapter(credential))
                .setApplicationName(APPLICATION_NAME)
                .build()

            var rawRows: List<List<Any?>>? = null
            val candidateRanges = listOf(range, "'Sheet1'!A2:E", "'contacts'!A2:E", "Sheet1!A2:E", "contacts!A2:E", "A2:E")

            for (candidateRange in candidateRanges.distinct()) {
                try {
                    Log.d(TAG, "Attempting fetch with range: $candidateRange")
                    val response = sheets.spreadsheets().values()
                        .get(spreadsheetId, candidateRange)
                        .execute()

                    val values = response.getValues()
                    if (!values.isNullOrEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        rawRows = values as List<List<Any?>>
                        Log.i(TAG, "Successfully retrieved ${rawRows.size} raw rows using range '$candidateRange'")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Range '$candidateRange' failed: ${e.message}")
                }
            }

            if (rawRows.isNullOrEmpty()) {
                Log.w(TAG, "No data found across all candidate ranges for Spreadsheet ID: $spreadsheetId")
                Log.i(TAG, "Successfully fetched 0 contacts")
                return emptyList()
            }

            val contacts = rawRows.mapIndexedNotNull { index, rowObject ->
                val row = rowObject as? List<*> ?: run {
                    Log.w(TAG, "Row at index $index is not a List: $rowObject")
                    return@mapIndexedNotNull null
                }

                if (row.isEmpty() || row.all { it == null || it.toString().isBlank() }) {
                    return@mapIndexedNotNull null
                }

                try {
                    // Col 0 (A): External ID (e.g. "c-1")
                    val rawId = row.getOrNull(0)?.toString()?.trim()
                    val contactId = if (!rawId.isNullOrEmpty()) rawId else "c-${index + 1}"

                    // Col 1 (B): Name (e.g. "أمال أبوبكر ودان")
                    val name = row.getOrNull(1)?.toString()?.trim().orEmpty()

                    // Col 2 (C): Administration / Department / Branch (e.g. "الموظفين")
                    val department = row.getOrNull(2)?.toString()?.trim().orEmpty()

                    // Col 3 (D): Phone Number (e.g. "091-264-0367")
                    val phone = row.getOrNull(3)?.toString()?.trim().orEmpty()

                    // Col 4 (E): Notes / Extra
                    val notes = row.getOrNull(4)?.toString()?.trim().orEmpty()

                    // Skip row only if both name and phone are completely blank
                    if (name.isBlank() && phone.isBlank()) {
                        Log.d(TAG, "Row $index skipped because both name and phone are blank.")
                        return@mapIndexedNotNull null
                    }

                    Contact(
                        contactId = contactId,
                        name = name,
                        phone = phone,
                        email = if (notes.contains("@")) notes else "",
                        address = department,
                        notes = notes
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing row $index: $row", e)
                    null
                }
            }

            Log.i(TAG, "Successfully fetched ${contacts.size} contacts from Google Sheets")
            Log.d(TAG, "Live fetched ${contacts.size} rows from Sheet")
            if (contacts.isNotEmpty()) {
                Log.d(TAG, "Sample fetched contacts: ${contacts.take(3).map { "${it.name} (${it.phone})" }}")
            }
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch contacts from Google Sheets", e)
            emptyList()
        }
    }

    private fun getCredentials(): GoogleCredentials {
        return try {
            val inputStream: InputStream = context.resources.openRawResource(R.raw.credentials)
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY))
            Log.d(TAG, "Successfully loaded Google credentials from res/raw/credentials.json")
            credentials
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load credentials from res/raw/credentials.json", e)
            throw e
        }
    }
}