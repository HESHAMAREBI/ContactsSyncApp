package com.example.contactssyncapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val contactId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val department: String = "",
    val jobTitle: String = ""
) {
    val phoneNumber: String
        get() = phone
}