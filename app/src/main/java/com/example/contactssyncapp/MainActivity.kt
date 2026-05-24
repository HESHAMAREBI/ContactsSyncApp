package com.example.contactssyncapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createNotificationChannel(this)

        // جدولة WorkManager كل 15 دقيقة
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.contactssyncapp.data.SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "contacts_sync_work",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        if (checkAndRequestPermissions()) {
            setupClickListeners()
        }

        // زر التعليمات
        val helpButton: android.widget.ImageButton = findViewById(R.id.helpButton)
        helpButton.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun showHelpDialog() {
        val message = """
تعليمات استخدام التطبيق:

1️⃣ المزامنة التلقائية:
يتم مزامنة جهات الاتصال تلقائيًا مع قاعدة بيانات المؤسسة بمجرد اتصال الهاتف بالإنترنت.

2️⃣ حذف جهات الاتصال القديمة:
قبل تثبيت التطبيق، يُفضَّل حذف أي جهات اتصال شخصية أو قديمة لتجنب التكرار،
وتأكد أيضًا من حذفها من سلة المحذوفات في تطبيق جهات الاتصال.

3️⃣ الإشعارات اليومية:
سيصلك إشعار كل 24 ساعة عند وجود تحديث جديد لجهات الاتصال، اضغط عليه لفتح التطبيق وتحديث القائمة.
""".trimIndent()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تعليمات الاستخدام")
            .setMessage(message)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        permissionsToRequest.add(Manifest.permission.WRITE_CONTACTS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNotGranted.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            return false
        }

        return true
    }

    private fun setupClickListeners() {
        val statusText: TextView = findViewById(R.id.statusText)
        val manualSyncButton: Button = findViewById(R.id.manualSyncButton)
        val deleteButton: Button = findViewById(R.id.deleteButton)

        statusText.text = "آخر مزامنة: لم تتم بعد"

        manualSyncButton.setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(this@MainActivity, "بدء المزامنة...", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) {
                    try {
                        val count = ContactsSyncRepository.syncContactsFromSheetWithCount(this@MainActivity)
                        withContext(Dispatchers.Main) {
                            statusText.text = "آخر مزامنة: $count جهة اتصال"
                            Toast.makeText(this@MainActivity, "تمت مزامنة $count جهة اتصال!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "فشلت المزامنة: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        deleteButton.setOnClickListener {
            val deleted = ContactsSyncRepository.deleteAppContacts(this)
            Toast.makeText(this, "تم حذف $deleted جهة اتصال!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val perms = HashMap<String, Int>()
            perms[Manifest.permission.READ_CONTACTS] = PackageManager.PERMISSION_GRANTED
            perms[Manifest.permission.WRITE_CONTACTS] = PackageManager.PERMISSION_GRANTED
            if (grantResults.isNotEmpty()) {
                for (i in permissions.indices) {
                    perms[permissions[i]] = grantResults[i]
                }
                if (perms[Manifest.permission.READ_CONTACTS] == PackageManager.PERMISSION_GRANTED &&
                    perms[Manifest.permission.WRITE_CONTACTS] == PackageManager.PERMISSION_GRANTED) {
                    setupClickListeners()
                } else {
                    Toast.makeText(this, "Permissions are required to use this app", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
