package com.avnikahraman.safedose.ui.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.databinding.ActivityAlarmBinding
import com.avnikahraman.safedose.utils.AlarmReceiver

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var snoozeCount = 0
    private val MAX_SNOOZE = 2

    companion object {
        const val EXTRA_MEDICINE_NAME = "medicine_name"
        const val EXTRA_TIME = "time"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kilit ekranında göster
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent'ten verileri al
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: "İlaç"
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        // Bilgileri göster
        binding.tvMedicineName.text = medicineName
        binding.tvTime.text = time
        binding.tvMessage.text = "İlaç alma zamanı!"

        // Ertele butonunu kontrol et
        updateSnoozeButton()

        // Ses ve titreşim başlat
        startAlarm()

        // Kapat butonu
        binding.btnDismiss.setOnClickListener {
            stopAlarm()
            navigateToMain()  // BUNU EKLE
        }


        // Ertele butonu
        binding.btnSnooze.setOnClickListener {
            if (snoozeCount < MAX_SNOOZE) {
                stopAlarm()
                scheduleSnooze(medicineName, time, alarmId)
                finish()
            }
        }
    }
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun updateSnoozeButton() {
        val remainingSnoozes = MAX_SNOOZE - snoozeCount

        if (snoozeCount >= MAX_SNOOZE) {
            binding.btnSnooze.isEnabled = false
            binding.btnSnooze.text = "Ertele Hakkınız Kalmadı"
            binding.btnSnooze.alpha = 0.5f
        } else {
            binding.btnSnooze.isEnabled = true
            binding.btnSnooze.text = "5 Dakika Ertele ($remainingSnoozes hak kaldı)"
            binding.btnSnooze.alpha = 1.0f
        }
    }

    private fun scheduleSnooze(medicineName: String, time: String, alarmId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount + 1)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId.hashCode() + snoozeCount + 1000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5 dakika sonra
        val triggerTime = System.currentTimeMillis() + (5 * 60 * 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun startAlarm() {
        // Alarm sesi
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Titreşim
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 500, 500, 500, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBackPressed() {
        // bilinçli olarak boş
    }

}