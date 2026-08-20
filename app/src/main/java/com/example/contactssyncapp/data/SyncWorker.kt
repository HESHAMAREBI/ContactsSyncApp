package com.example.contactssyncapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Result.retry()
        }

        val googleSheetsRepository = GoogleSheetsRepository(applicationContext)
        val contactsSyncRepository = ContactsSyncRepository(applicationContext)

        return try {
            val contacts = googleSheetsRepository.getContacts()

            // حفظ واسترجاع النسخة المحلية من جهات الاتصال
            val prefs = applicationContext.getSharedPreferences("contacts_sync_prefs", Context.MODE_PRIVATE)
            val oldContactsJson = prefs.getString("last_contacts_json", null)
            val gson = com.google.gson.Gson()

            // ترتيب جهات الاتصال حسب contactId لضمان مقارنة صحيحة
            val sortedNewContacts = contacts.sortedBy { it.contactId }
            val newContactsJson = gson.toJson(sortedNewContacts)

            val hasUpdate: Boolean = if (oldContactsJson == null) {
                // أول مزامنة -> لا يوجد تحديث، فقط حفظ البيانات
                false
            } else {
                // مقارنة JSON مباشرة - إذا تغيرت البيانات سيكون JSON مختلف
                oldContactsJson != newContactsJson
            }

            if (hasUpdate) {
                // إرسال إشعار تحديث فقط عند وجود تغيير فعلي
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                com.example.contactssyncapp.NotificationHelper.showContactsUpdateNotification(applicationContext, date)
            }

            // تحديث النسخة المحلية
            prefs.edit().putString("last_contacts_json", newContactsJson).apply()

            contactsSyncRepository.syncContacts(contacts)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
