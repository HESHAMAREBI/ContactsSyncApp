package com.example.contactssyncapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    /**
     * يعرض رسالة للمستخدم إذا كانت إشعارات التطبيق متوقفة من النظام
     */
    fun checkNotificationsEnabledAndWarn(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val areEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true // لا يمكن الفحص في الإصدارات القديمة
        }
        if (!areEnabled) {
            android.app.AlertDialog.Builder(context)
                .setTitle("تنبيه الإشعارات")
                .setMessage("الإشعارات متوقفة لهذا التطبيق. يرجى تفعيلها من إعدادات النظام لضمان وصول التنبيهات.")
                .setPositiveButton("فتح الإعدادات") { _, _ ->
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }
    fun showContactsUpdateNotification(context: Context, date: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent لفتح MainActivity عند الضغط على الإشعار
        val intent = android.content.Intent(context, MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val message = "يوجد تحديث جهات اتصال جديد بتاريخ $date"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تحديث جهات الاتصال")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(2, builder.build())
    }

    private const val CHANNEL_ID = "contacts_sync_channel"
    private const val CHANNEL_NAME = "Contacts Sync"
    private const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for contacts sync notifications"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showSyncCompleteNotification(context: Context, count: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val message = if (count > 0) {
            "تمت مزامنة $count جهة اتصال بنجاح."
        } else {
            "لا توجد جهات اتصال جديدة للمزامنة."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure you have this drawable
            .setContentTitle("اكتملت مزامنة جهات الاتصال")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}