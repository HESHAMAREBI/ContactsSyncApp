package com.example.contactssyncapp.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactssyncapp.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Syncing : SyncUiState()
    data class Success(val result: SyncResult, val timestamp: String) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ManualSync"
    }

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val googleSheetsRepository = GoogleSheetsRepository(application)
    private val contactsSyncRepository = ContactsSyncRepository(application)

    fun triggerManualSync() {
        if (_uiState.value is SyncUiState.Syncing) {
            Log.d(TAG, "Sync is already in progress, ignoring trigger.")
            return
        }

        _uiState.value = SyncUiState.Syncing
        Log.i(TAG, "Manual sync triggered from UI...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sheetContacts = googleSheetsRepository.getContacts()
                Log.i(TAG, "Fetched ${sheetContacts.size} contacts from Google Sheets. Executing Smart Diff...")

                val syncResult = contactsSyncRepository.syncContactsWithDiff(sheetContacts)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                NotificationHelper.showSyncCompleteNotification(getApplication(), syncResult.totalActive)
                Log.i(TAG, "Smart Diff completed successfully: +${syncResult.inserted} new, ~${syncResult.updated} updated, -${syncResult.deleted} deleted, =${syncResult.unchanged} unchanged")

                _uiState.value = SyncUiState.Success(result = syncResult, timestamp = timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Smart Diff failed with error: ${e.message}", e)
                _uiState.value = SyncUiState.Error(e.message ?: "فشلت عملية المزامنة")
            }
        }
    }

    fun deleteCorporateContacts(onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedCount = ContactsSyncRepository.deleteAppContacts(getApplication())
            _uiState.value = SyncUiState.Idle
            onComplete(deletedCount)
        }
    }
}
