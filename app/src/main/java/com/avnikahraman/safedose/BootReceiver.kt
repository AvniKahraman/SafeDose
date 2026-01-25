package com.avnikahraman.safedose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.avnikahraman.safedose.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Cihaz yeniden başlatıldığında tüm alarmları yeniden kurar
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "📱 Cihaz yeniden başlatıldı, alarmlar kuruluyor...")

            val repository = FirebaseRepository.getInstance()
            val userId = repository.getCurrentUser()?.uid

            if (userId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = repository.getUserAlarms(userId)

                        if (result.isSuccess) {
                            val alarms = result.getOrNull() ?: emptyList()
                            Log.d(TAG, "✅ ${alarms.size} alarm bulundu")

                            // Ana thread'de alarmları kur
                            CoroutineScope(Dispatchers.Main).launch {
                                alarms.forEach { alarm ->
                                    AlarmScheduler.scheduleAlarm(context, alarm)
                                }
                                Log.d(TAG, "✅ ${alarms.size} alarm yeniden kuruldu")
                            }
                        } else {
                            Log.e(TAG, "❌ Alarmlar alınamadı: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Hata: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Kullanıcı oturumu yok")
            }
        }
    }
}