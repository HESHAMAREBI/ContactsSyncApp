package com.example.contactssyncapp.data

import android.content.Context
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.example.contactssyncapp.R
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.InputStream

@Suppress("UNCHECKED_CAST")
class GoogleSheetsRepository(private val context: Context) {

    fun getContacts(): List<Contact> {
        try {
            android.util.Log.d("SheetsDebug", "جلب البيانات من Google Sheets...")
            val credential = getCredentials()
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val sheets = Sheets.Builder(transport, jsonFactory, HttpCredentialsAdapter(credential))
                .setApplicationName("ContactsSyncApp")
                .build()

            val spreadsheetId = "1OaoYcShJJbCEmECf-_HX3Kwc5REqec2W_mPGU4H6r-I"
            val range = "Sheet1!A2:F"

            val result = sheets.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()

            val values = result.values // This will be List<Any?>

            if (values.isNullOrEmpty()) {
                android.util.Log.w("SheetsDebug", "No data found in sheet.")
                return emptyList()
            }

            android.util.Log.d("SheetsDebug", "Original data from sheet: $values")
            android.util.Log.d("SheetsDebug", "Type of result.values: ${values.javaClass.name}")
            values.firstOrNull()?.let {
                android.util.Log.d("SheetsDebug", "Type of first element in values: ${it.javaClass.name}")
            }

            val contacts = values.flatMap { it as? List<*> ?: emptyList() }
                .mapNotNull { rowObject ->
                    // rows from Sheets may contain null cells, so treat elements as nullable Any?
                    val rowList = rowObject as? List<Any?>

                    if (rowList.isNullOrEmpty() || rowList.all { it?.toString()?.isBlank() == true }) {
                        android.util.Log.d("SheetsDebug", "Skipping empty or blank row: $rowList")
                        return@mapNotNull null
                    }

                    try {
                        val contactId = rowList.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() }
                            ?: java.util.UUID.randomUUID().toString()

                        android.util.Log.d("SheetsDebug", "Generated contactId: $contactId for row: $rowList")

                        Contact(
                            contactId = contactId,
                            name = rowList.getOrNull(1)?.toString() ?: "",
                            phone = rowList.getOrNull(4)?.toString() ?: "",
                            email = "", // No email column in the sheet
                            address = rowList.getOrNull(2)?.toString() ?: "",
                            notes = rowList.getOrNull(3)?.toString() ?: ""
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("SheetsDebug", "Failed to parse row: $rowList", e)
                        null
                    }
                }

            android.util.Log.d("SheetsDebug", "عدد جهات الاتصال التي تم تحويلها: ${contacts.size}")
            return contacts
        } catch (e: Exception) {
            android.util.Log.e("SheetsDebug", "Error fetching contacts from Google Sheets", e)
            return emptyList()
        }
    }

    private fun getCredentials(): GoogleCredentials {
        val inputStream: InputStream = context.resources.openRawResource(R.raw.credentials)
        return GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY))
    }
}