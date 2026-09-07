package com.example.contactssyncapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactssyncapp.data.Contact
import com.example.contactssyncapp.data.ContactsSyncRepository
import com.example.contactssyncapp.data.GoogleSheetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ContactsDirectoryUiState {
    object Loading : ContactsDirectoryUiState()
    data class Success(
        val totalCount: Int,
        val filteredList: List<Contact>,
        val query: String = ""
    ) : ContactsDirectoryUiState()
    data class Error(val message: String) : ContactsDirectoryUiState()
}

class ContactsDirectoryViewModel(
    private val contactsRepo: ContactsSyncRepository,
    private val sheetsRepo: GoogleSheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactsDirectoryUiState>(ContactsDirectoryUiState.Loading)
    val uiState: StateFlow<ContactsDirectoryUiState> = _uiState.asStateFlow()

    private var cachedContacts: List<Contact> = emptyList()
    private var currentQuery: String = ""

    companion object {
        private const val TAG = "ContactsDirectoryVM"
    }

    fun loadContacts() {
        _uiState.value = ContactsDirectoryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var contacts = contactsRepo.getStoredEnterpriseContacts()
                if (contacts.isEmpty()) {
                    android.util.Log.d(TAG, "Stored enterprise contacts is empty, falling back to Google Sheets...")
                    contacts = sheetsRepo.getContacts()
                }
                cachedContacts = contacts
                applyFilter(currentQuery)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading contacts", e)
                _uiState.value = ContactsDirectoryUiState.Error(e.message ?: "حدث خطأ أثناء تحميل جهات الاتصال")
            }
        }
    }

    fun syncFreshContacts() {
        _uiState.value = ContactsDirectoryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val freshSheetContacts = sheetsRepo.getContacts()
                if (freshSheetContacts.isNotEmpty()) {
                    contactsRepo.syncContactsWithDiff(freshSheetContacts)
                }
                var updatedContacts = contactsRepo.getStoredEnterpriseContacts()
                if (updatedContacts.isEmpty()) {
                    updatedContacts = freshSheetContacts
                }
                cachedContacts = updatedContacts
                applyFilter(currentQuery)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error syncing fresh contacts", e)
                _uiState.value = ContactsDirectoryUiState.Error(e.message ?: "حدث خطأ أثناء مزامنة وتحديث البيانات")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        currentQuery = query
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        val trimmed = query.trim().lowercase()
        val filtered = if (trimmed.isEmpty()) {
            cachedContacts
        } else {
            val normalizedQueryDigits = trimmed.replace(Regex("[^0-9]"), "")
            cachedContacts.filter { contact ->
                val nameMatch = contact.name.lowercase().contains(trimmed)
                val deptMatch = contact.department.lowercase().contains(trimmed) || contact.address.lowercase().contains(trimmed)
                val titleMatch = contact.jobTitle.lowercase().contains(trimmed)
                val notesMatch = contact.notes.lowercase().contains(trimmed)
                val phoneMatch = if (normalizedQueryDigits.isNotEmpty()) {
                    contact.phone.replace(Regex("[^0-9]"), "").contains(normalizedQueryDigits)
                } else {
                    contact.phone.contains(trimmed)
                }

                nameMatch || deptMatch || titleMatch || notesMatch || phoneMatch
            }
        }

        _uiState.value = ContactsDirectoryUiState.Success(
            totalCount = cachedContacts.size,
            filteredList = filtered,
            query = query
        )
    }
}
