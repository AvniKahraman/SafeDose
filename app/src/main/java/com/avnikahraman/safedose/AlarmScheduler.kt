package com.avnikahraman.safedose

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.avnikahraman.safedose.models.Alarm
import com.avnikahraman.safedose.utils.AlarmReceiver
import java.util.*

/**
 * Android AlarmManager ile alarm zamanlama
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /**
     * Alarm kur
     */
    fun scheduleAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("medicine_name", alarm.medicineName)
            putExtra("time", alarm.timeString)
            putExtra("snooze_count", 0)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Exact alarm izni yoksa ayarlara yönlendir
                    Log.w(TAG, "Exact alarm izni yok")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            Log.d(TAG, "Alarm scheduled: ${alarm.medicineName} at ${alarm.timeString}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm: ${e.message}", e)
        }
    }
    /**
     * Alarmı iptal et
     */
    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarm iptal edildi: ${alarm.medicineName}")
    }

    /**
     * Tekrarlayan alarm kur (her gün aynı saatte)
     */
    fun scheduleRepeatingAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("medicine_name", alarm.medicineName)
            putExtra("time", alarm.timeString)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            // Günlük tekrarlayan alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )

            Log.d(TAG, "Tekrarlayan alarm kuruldu: ${alarm.medicineName}")
        } catch (e: Exception) {
            Log.e(TAG, "Tekrarlayan alarm kurulurken hata: ${e.message}", e)
        }
    }
}