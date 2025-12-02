package com.avnikahraman.safedose.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.R
import com.avnikahraman.safedose.ui.alarm.AlarmActivity

/**
 * Alarm tetiklendiğinde bildirim gösteren BroadcastReceiver
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "medicine_alarm_channel"
        private const val CHANNEL_NAME = "İlaç Hatırlatmaları"
        private const val CHANNEL_DESCRIPTION = "İlaç alma zamanı bildirimleri"
        private const val TAG = "AlarmReceiver"

    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicine_name") ?: "İlaç"
        val time = intent.getStringExtra("time") ?: ""
        val alarmId = intent.getStringExtra("alarm_id") ?: ""
        val snoozeCount = intent.getIntExtra("snooze_count", 0)

        Log.d(TAG, "Alarm received: $medicineName at $time (snooze: $snoozeCount)")

        // Full screen alarm activity'yi başlat
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlarmActivity.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmActivity.EXTRA_TIME, time)
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        context.startActivity(alarmIntent)
    }



    /**
     * Bildirim göster
     */
    private fun showNotification(
        context: Context,
        medicineName: String,
        time: String,
        alarmId: String
    ) {
        // Notification channel oluştur (Android 8.0+)
        createNotificationChannel(context)

        // Ana ekrana gidecek intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification ses
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Notification oluştur
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: İlaç ikonu eklenebilir
            .setContentTitle("💊 İlaç Zamanı!")
            .setContentText("$medicineName - $time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$medicineName ilacınızı alma zamanı geldi.\n\nSaat: $time")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Notification Manager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Unique notification ID oluştur
        val notificationId = alarmId.hashCode()

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Notification Channel oluştur (Android 8.0+)
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


}