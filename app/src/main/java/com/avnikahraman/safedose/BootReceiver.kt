package com.avnikahraman.safedose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.avnikahraman.safedose.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, rescheduling alarms")

            // Firebase'den alarmları çek ve yeniden kur
            val repository = FirebaseRepository.getInstance()
            val userId = repository.getCurrentUser()?.uid

            if (userId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val result = repository.getUserAlarms(userId)
                    if (result.isSuccess) {
                        val alarms = result.getOrNull() ?: emptyList()
                        alarms.forEach { alarm ->
                            AlarmScheduler.scheduleAlarm(context, alarm)
                        }
                        Log.d("BootReceiver", "Rescheduled ${alarms.size} alarms")
                    }
                }
            }
        }
    }
}