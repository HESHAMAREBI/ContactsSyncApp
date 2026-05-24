package com.example.contactssyncapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<Contact>)

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM contacts WHERE contactId IN (:ids)")
    suspend fun getContactsByIds(ids: List<String>): List<Contact>

    @Query("DELETE FROM contacts WHERE contactId NOT IN (:idsToKeep)")
    suspend fun deleteOldContacts(idsToKeep: List<String>)
}
