package com.example.contactssyncapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.example.contactssyncapp.ui.ContactsDirectoryActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    private lateinit var lastSyncTextView: TextView
    private lateinit var detailedStatsTextView: TextView
    private lateinit var syncProgressBar: ProgressBar
    private lateinit var syncButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private lateinit var helpButton: ImageButton
    private lateinit var directoryCard: MaterialCardView

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
        lastSyncTextView = findViewById(R.id.lastSyncTextView)
        detailedStatsTextView = findViewById(R.id.detailedStatsTextView)
        syncProgressBar = findViewById(R.id.syncProgressBar)
        syncButton = findViewById(R.id.syncButton)
        deleteButton = findViewById(R.id.deleteButton)
        helpButton = findViewById(R.id.helpButton)
        directoryCard = findViewById(R.id.directoryCard)

        lastSyncTextView.text = getString(R.string.last_sync_never)
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
                            syncButton.isEnabled = true
                            deleteButton.isEnabled = true
                        }
                        is SyncUiState.Syncing -> {
                            syncProgressBar.visibility = View.VISIBLE
                            syncButton.isEnabled = false
                            deleteButton.isEnabled = false
                            lastSyncTextView.text = getString(R.string.status_syncing)
                            detailedStatsTextView.visibility = View.GONE
                        }
                        is SyncUiState.Success -> {
                            syncProgressBar.visibility = View.GONE
                            syncButton.isEnabled = true
                            deleteButton.isEnabled = true
                            lastSyncTextView.text = getString(R.string.last_sync_format, state.timestamp)
                            detailedStatsTextView.text = getString(
                                R.string.diff_summary_format,
                                state.result.inserted,
                                state.result.updated,
                                state.result.unchanged,
                                state.result.deleted
                            )
                            detailedStatsTextView.visibility = View.VISIBLE
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.sync_success_toast, state.result.totalActive),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is SyncUiState.Error -> {
                            syncProgressBar.visibility = View.GONE
                            syncButton.isEnabled = true
                            deleteButton.isEnabled = true
                            lastSyncTextView.text = getString(R.string.sync_error_toast, state.message)
                            detailedStatsTextView.visibility = View.GONE
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
        syncButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                viewModel.triggerManualSync()
            } else {
                requestRequiredPermissions()
            }
        }

        directoryCard.setOnClickListener {
            if (hasRequiredPermissions()) {
                startActivity(Intent(this, ContactsDirectoryActivity::class.java))
            } else {
                requestRequiredPermissions()
            }
        }

        deleteButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                showDeleteConfirmationDialog()
            } else {
                requestRequiredPermissions()
            }
        }

        helpButton.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message))
            .setPositiveButton(getString(R.string.confirm_yes_delete)) { _, _ ->
                viewModel.deleteCorporateContacts { deletedCount ->
                    runOnUiThread {
                        lastSyncTextView.text = getString(R.string.status_all_deleted)
                        detailedStatsTextView.visibility = View.GONE
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.delete_success_toast, deletedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showHelpDialog() {
        val spreadsheetId = AppConfigManager.getSpreadsheetId(this)
        val sheetRange = AppConfigManager.getSheetRange(this)
        val intervalHours = AppConfigManager.getSyncIntervalHours(this)

        val message = """
            • معرّف جدول البيانات:
            $spreadsheetId
            
            • النطاق المستهدف:
            $sheetRange
            
            • فترة المزامنة التلقائية:
            كل $intervalHours ساعة
            
            • حماية جهات الاتصال:
            تتم مزامنة جهات الاتصال حصراً تحت حساب "com.jumhoria.contacts" ولا يتم تعديل أو حذف جهات اتصالك الشخصية أبداً.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.help_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.help_ok), null)
            .show()
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasRead = hasPermission(Manifest.permission.READ_CONTACTS)
        val hasWrite = hasPermission(Manifest.permission.WRITE_CONTACTS)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        return hasRead && hasWrite && hasNotification
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
