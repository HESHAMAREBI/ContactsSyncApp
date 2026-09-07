package com.example.contactssyncapp.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppConfigManager {

    private const val TAG = "AppConfigManager"

    // Restriction Keys
    private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    private const val KEY_SHEET_RANGE = "sheet_range"
    private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
    private const val KEY_FORCE_ACCOUNT_NAME = "force_account_name"

    // Default Fallbacks
    const val DEFAULT_SPREADSHEET_ID = "1OaoYcShJJbCEmECf-_HX3Kwc5REqec2W_mPGU4H6r-I"
    const val DEFAULT_SHEET_RANGE = "'Sheet1'!A2:F"
    const val DEFAULT_SYNC_INTERVAL_HOURS = 24L
    const val DEFAULT_ACCOUNT_NAME = "مصرف الجمهورية"
    const val UNIQUE_WORK_NAME = "contacts_sync_work"

    private fun getRestrictions(context: Context): Bundle? {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
        return try {
            restrictionsManager?.applicationRestrictions
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ApplicationRestrictions: ${e.message}")
            null
        }
    }

    fun getSpreadsheetId(context: Context): String {
        val restrictions = getRestrictions(context)
        val value = restrictions?.getString(KEY_SPREADSHEET_ID)?.trim()
        val result = if (!value.isNullOrEmpty()) value else DEFAULT_SPREADSHEET_ID
        Log.d(TAG, "Active Spreadsheet ID: $result (MDM managed: ${!value.isNullOrEmpty()})")
        return result
    }

    fun getSheetRange(context: Context): String {
        val restrictions = getRestrictions(context)
        val value = restrictions?.getString(KEY_SHEET_RANGE)?.trim()
        val result = if (!value.isNullOrEmpty()) value else DEFAULT_SHEET_RANGE
        Log.d(TAG, "Active Sheet Range: $result (MDM managed: ${!value.isNullOrEmpty()})")
        return result
    }

    fun getSyncIntervalHours(context: Context): Long {
        val restrictions = getRestrictions(context)
        val value = restrictions?.getInt(KEY_SYNC_INTERVAL_HOURS, DEFAULT_SYNC_INTERVAL_HOURS.toInt()) ?: DEFAULT_SYNC_INTERVAL_HOURS.toInt()
        val result = if (value > 0) value.toLong() else DEFAULT_SYNC_INTERVAL_HOURS
        Log.d(TAG, "Active Sync Interval: $result hours (MDM managed: ${restrictions?.containsKey(KEY_SYNC_INTERVAL_HOURS) == true})")
        return result
    }

    fun getAccountName(context: Context): String {
        val restrictions = getRestrictions(context)
        val value = restrictions?.getString(KEY_FORCE_ACCOUNT_NAME)?.trim()
        val result = if (!value.isNullOrEmpty()) value else DEFAULT_ACCOUNT_NAME
        Log.d(TAG, "Active Account Name: $result (MDM managed: ${!value.isNullOrEmpty()})")
        return result
    }

    /**
     * Schedules periodic background sync using WorkManager with the configured interval
     * and network connected constraints.
     */
    fun schedulePeriodicSync(context: Context, updateExisting: Boolean = false) {
        val intervalHours = getSyncIntervalHours(context)
        Log.i(TAG, "Scheduling periodic sync every $intervalHours hours (updateExisting=$updateExisting)...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        val policy = if (updateExisting) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            syncRequest
        )
    }

    /**
     * BroadcastReceiver responding to remote MDM configuration updates in real-time.
     */
    class RestrictionsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                Log.i(TAG, "MDM Managed Configuration changed! Reloading configurations and rescheduling sync...")
                schedulePeriodicSync(context, updateExisting = true)
            }
        }
    }
}
