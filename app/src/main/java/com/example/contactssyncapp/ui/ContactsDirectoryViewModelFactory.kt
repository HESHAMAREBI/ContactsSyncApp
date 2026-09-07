package com.example.contactssyncapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.contactssyncapp.data.ContactsSyncRepository
import com.example.contactssyncapp.data.GoogleSheetsRepository

class ContactsDirectoryViewModelFactory(
    private val repository: ContactsSyncRepository,
    private val sheetsRepository: GoogleSheetsRepository = GoogleSheetsRepository(repository.context)
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsDirectoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsDirectoryViewModel(repository, sheetsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
