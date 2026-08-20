package com.example.contactssyncapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.contactssyncapp.data.AppConfigManager
import com.example.contactssyncapp.data.MainViewModel
import com.example.contactssyncapp.data.MainViewModelFactory
import com.example.contactssyncapp.data.SyncUiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    private lateinit var statusText: TextView
    private lateinit var diffDetailsText: TextView
    private lateinit var syncProgressBar: ProgressBar
    private lateinit var manualSyncButton: Button
    private lateinit var deleteButton: Button
    private lateinit var helpButton: ImageButton

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readContactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: hasPermission(Manifest.permission.READ_CONTACTS)
        val writeContactsGranted = permissions[Manifest.permission.WRITE_CONTACTS] ?: hasPermission(Manifest.permission.WRITE_CONTACTS)

        if (readContactsGranted && writeContactsGranted) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, getString(R.string.permissions_required_toast), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        NotificationHelper.createNotificationChannel(this)
        observeUiState()
        setupClickListeners()

        if (hasRequiredPermissions()) {
            onPermissionsGranted()
        } else {
            requestRequiredPermissions()
        }
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        diffDetailsText = findViewById(R.id.diffDetailsText)
        syncProgressBar = findViewById(R.id.syncProgressBar)
        manualSyncButton = findViewById(R.id.manualSyncButton)
        deleteButton = findViewById(R.id.deleteButton)
        helpButton = findViewById(R.id.helpButton)

        statusText.text = getString(R.string.last_sync_never)
    }

    private fun onPermissionsGranted() {
        AppConfigManager.schedulePeriodicSync(this)
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SyncUiState.Idle -> {
                            syncProgressBar.visibility = View.GONE
                            manualSyncButton.isEnabled = true
                            deleteButton.isEnabled = true
                        }
                        is SyncUiState.Syncing -> {
                            syncProgressBar.visibility = View.VISIBLE
                            manualSyncButton.isEnabled = false
                            deleteButton.isEnabled = false
                            statusText.text = getString(R.string.status_syncing)
                            diffDetailsText.visibility = View.GONE
                        }
                        is SyncUiState.Success -> {
                            syncProgressBar.visibility = View.GONE
                            manualSyncButton.isEnabled = true
                            deleteButton.isEnabled = true

                            statusText.text = getString(R.string.last_sync_format, state.timestamp)
                            val summary = if (state.result.unchanged > 0) {
                                getString(
                                    R.string.diff_summary_format,
                                    state.result.inserted,
                                    state.result.updated,
                                    state.result.unchanged,
                                    state.result.deleted
                                )
                            } else {
                                getString(
                                    R.string.diff_summary_format_no_unchanged,
                                    state.result.inserted,
                                    state.result.updated,
                                    state.result.deleted
                                )
                            }
                            diffDetailsText.text = summary
                            diffDetailsText.visibility = View.VISIBLE

                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.sync_success_toast, state.result.totalActive),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is SyncUiState.Error -> {
                            syncProgressBar.visibility = View.GONE
                            manualSyncButton.isEnabled = true
                            deleteButton.isEnabled = true
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.sync_error_toast, state.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        manualSyncButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                viewModel.triggerManualSync()
            } else {
                requestRequiredPermissions()
            }
        }

        deleteButton.setOnClickListener {
            if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
                requestRequiredPermissions()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_confirm_title))
                .setMessage(getString(R.string.delete_confirm_message))
                .setPositiveButton(getString(R.string.confirm_yes_delete)) { _, _ ->
                    viewModel.deleteCorporateContacts { deletedCount ->
                        runOnUiThread {
                            statusText.text = getString(R.string.status_all_deleted)
                            diffDetailsText.visibility = View.GONE
                            Toast.makeText(
                                this,
                                getString(R.string.delete_success_toast, deletedCount),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        helpButton.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun showHelpDialog() {
        val message = """
تعليمات استخدام التطبيق:

1️⃣ المزامنة التلقائية:
يتم مزامنة جهات الاتصال تلقائيًا مع قاعدة بيانات المؤسسة بمجرد اتصال الهاتف بالإنترنت.

2️⃣ المزامنة اليدوية الفورية:
يمكنك الضغط على زر 'مزامنة جهات الاتصال الآن' لتحديث جهات الاتصال مباشرة ومقارنة التعديلات.

3️⃣ حذف جهات الاتصال المؤسسية:
يتيح لك زر 'حذف جهات الاتصال المؤسسية' مسح الأرقام التي أضافها التطبيق فقط دون المساس بأرقامك الشخصية.
""".trimIndent()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.help_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.help_ok), null)
            .show()
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredPermissions(): Boolean {
        val contactsGranted = hasPermission(Manifest.permission.READ_CONTACTS) &&
                hasPermission(Manifest.permission.WRITE_CONTACTS)
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        return contactsGranted && notificationsGranted
    }

    private fun requestRequiredPermissions() {
        requestPermissionsLauncher.launch(getRequiredPermissions())
    }
}
