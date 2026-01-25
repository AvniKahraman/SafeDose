package com.avnikahraman.safedose.ui.alarm

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.databinding.ActivityAlarmSetupBinding
import com.avnikahraman.safedose.models.Alarm
import com.avnikahraman.safedose.models.Medicine
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.auth.scanner.ScannerActivity
import com.avnikahraman.safedose.AlarmScheduler
import kotlinx.coroutines.launch
import java.util.*

class AlarmSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmSetupBinding
    private lateinit var repository: FirebaseRepository

    private var selectedHour = 8
    private var selectedMinute = 0

    private var barcode = ""
    private var medicineName = ""
    private var imageUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        barcode = intent.getStringExtra(ScannerActivity.EXTRA_BARCODE) ?: ""
        medicineName = intent.getStringExtra(ScannerActivity.EXTRA_MEDICINE_NAME) ?: ""
        imageUrl = intent.getStringExtra(ScannerActivity.EXTRA_MEDICINE_IMAGE) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Alarm Kur"

        setDefaultValues()
        setupClickListeners()
        checkRequiredPermissions()
    }
    private fun checkRequiredPermissions() {

        // Android 13+ Notification izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun setDefaultValues() {
        val calendar = Calendar.getInstance()
        selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
        selectedMinute = calendar.get(Calendar.MINUTE)
        updateStartTimeText()

        if (medicineName.isNotEmpty()) {
            binding.etMedicineName.setText(medicineName)
        }

        if (barcode.isNotEmpty()) {
            binding.tvBarcodeInfo.text = "Barkod: $barcode"
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectStartTime.setOnClickListener { showTimePicker() }
        binding.btnSave.setOnClickListener { validateAndSave() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, h, m ->
                selectedHour = h
                selectedMinute = m
                updateStartTimeText()
            },
            selectedHour,
            selectedMinute,
            true
        ).show()
    }

    private fun updateStartTimeText() {
        binding.tvStartTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
    }

    private fun validateAndSave() {

        val name = binding.etMedicineName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etMedicineName.error = "İlaç adı gerekli"
            return
        }

        val dosage = binding.etDosage.text.toString().trim()
        if (dosage.isEmpty()) {
            binding.etDosage.error = "Dozaj gerekli"
            return
        }

        val timesPerDay = binding.etTimesPerDay.text.toString().toIntOrNull()
        if (timesPerDay == null || timesPerDay !in 1..6) {
            binding.etTimesPerDay.error = "1-6 arası girin"
            return
        }

        val durationDays = binding.etDurationDays.text.toString().toIntOrNull()
        if (durationDays == null || durationDays !in 1..365) {
            binding.etDurationDays.error = "1-365 arası girin"
            return
        }

        val description = binding.etDescription.text.toString().trim()

        saveMedicineAndAlarms(
            name,
            dosage,
            intervalHours = 24 / timesPerDay,
            durationDays,
            description
        )
    }

    private fun saveMedicineAndAlarms(
        name: String,
        dosage: String,
        intervalHours: Int,
        durationDays: Int,
        description: String
    ) {
        val userId = repository.getCurrentUser()?.uid ?: return

        lifecycleScope.launch {
            val startTime = String.format("%02d:%02d", selectedHour, selectedMinute)

            val medicine = Medicine(
                id = "",
                barcode = barcode,
                name = name,
                dosage = dosage,
                imageUrl = imageUrl,
                description = description,
                startTime = startTime,
                intervalHours = intervalHours,
                durationDays = durationDays,
                startDate = System.currentTimeMillis(),
                userId = userId,
                createdAt = 0L,
                active = true
            )

            val medicineResult = repository.addMedicine(medicine)
            if (medicineResult.isFailure) return@launch

            val medicineId = medicineResult.getOrNull() ?: return@launch

            val alarms = createAlarms(
                medicineId,
                name,
                userId
            )



            alarms.forEach {
                repository.addAlarm(it)
                AlarmScheduler.scheduleAlarm(this@AlarmSetupActivity, it)
            }

            runOnUiThread {
                Toast.makeText(this@AlarmSetupActivity, "✅ Alarm kuruldu", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@AlarmSetupActivity, MainActivity::class.java))
                finish()
            }
        }
    }
    private fun createAlarms(
        medicineId: String,
        medicineName: String,
        userId: String
    ): List<Alarm> {

        val alarms = mutableListOf<Alarm>()
        val doseTimes = getDoseTimes() // Kullanıcının seçtiği saatleri alacak fonksiyon

        doseTimes.forEachIndexed { index, time ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.first)
                set(Calendar.MINUTE, time.second)
                set(Calendar.SECOND, 0)

                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            alarms.add(
                Alarm(
                    medicineId = medicineId,
                    medicineName = medicineName,
                    userId = userId,
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    minute = cal.get(Calendar.MINUTE),
                    timeString = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                    requestCode = generateRequestCode(medicineId, index)
                )
            )
        }

        return alarms
    }
    private fun getDoseTimes(): List<Pair<Int, Int>> {
        // Örnek: kullanıcı ilk dozu seçiyor, günde 3 defa, saatler otomatik hesaplanacak
        val timesPerDay = binding.etTimesPerDay.text.toString().toInt()
        val hours = mutableListOf<Pair<Int, Int>>()
        val startHour = selectedHour
        val startMinute = selectedMinute
        val interval = 24 / timesPerDay

        repeat(timesPerDay) { i ->
            var hour = startHour + i * interval
            var dayIncrement = 0
            if (hour >= 24) {
                hour -= 24
                dayIncrement = 1
            }
            hours.add(Pair(hour, startMinute))
        }
        return hours
    }




    private fun generateRequestCode(medicineId: String, index: Int): Int {
        return (medicineId.hashCode() + index) and 0x7FFFFFFF
    }
}
