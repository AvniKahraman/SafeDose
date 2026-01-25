package com.avnikahraman.safedose

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.avnikahraman.safedose.models.Alarm
import com.avnikahraman.safedose.utils.AlarmReceiver
import java.util.*

/**
 * Android AlarmManager ile alarm zamanlama
 * UI kasmasını önlemek için limitli ve güvenli hale getirildi
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val MAX_ALARMS = 7   // 🔥 SUNUM İÇİN LIMIT

    /**
     * Android 12+ exact alarm izni kontrolü
     */
    fun checkAndRequestAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }
        }
        return true
    }

    /**
     * Alarm kur
     */
    fun scheduleAlarm(context: Context, alarm: Alarm) {

        // 🔥 AYNI ALARM VARSA ÖNCE SİL (KASMA FIX)
        cancelAlarm(context, alarm)

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("medicine_name", alarm.medicineName)
            putExtra("time", alarm.timeString)
            putExtra("snooze_count", 0)

            // 🔥 UNIQUE ACTION
            action = "com.avnikahraman.safedose.ALARM_${alarm.requestCode}"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_MONTH, 1)
                Log.d(TAG, "⏰ Alarm zamanı geçmiş, yarına alındı")
            }
        }

        val delaySeconds = (calendar.timeInMillis - now) / 1000
        Log.d(TAG, "📅 Alarm kurulacak: ${alarm.timeString} (${delaySeconds}s)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    checkAndRequestAlarmPermission(context)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            Log.d(TAG, "✅ Alarm kuruldu: ${alarm.medicineName} ${alarm.timeString}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Alarm hatası", e)
        }
    }

    /**
     * Alarm iptal
     */
    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.avnikahraman.safedose.ALARM_${alarm.requestCode}"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        Log.d(TAG, "🔕 Alarm iptal edildi: ${alarm.medicineName}")
    }

    /**
     * Günlük alarm (tekrar)
     */
    fun scheduleRepeatingAlarm(context: Context, alarm: Alarm) {
        scheduleAlarm(context, alarm)
    }

    /**
     * TÜM alarmları yeniden kur (LIMITLI)
     * 🔥 UI KASMA FIX BURADA
     */
    fun rescheduleAllAlarms(context: Context, alarms: List<Alarm>) {
        Log.d(TAG, "📱 Alarmlar yeniden kuruluyor (${alarms.size})")

        alarms
            .take(MAX_ALARMS)
            .forEach { alarm ->
                scheduleAlarm(context, alarm)
            }
    }
}
