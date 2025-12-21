package com.avnikahraman.safedose.ui.alarm

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

/**
 * Alarm kurma ekranı
 * Kullanıcıdan ilaç kullanım bilgilerini alır
 */
class AlarmSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmSetupBinding
    private lateinit var repository: FirebaseRepository
    private var selectedHour: Int = 8
    private var selectedMinute: Int = 0
    private var barcode: String = ""
    private var medicineName: String = ""
    private var imageUrl: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlarmSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        // Intent'ten verileri al
        barcode = intent.getStringExtra(ScannerActivity.EXTRA_BARCODE) ?: ""
        medicineName = intent.getStringExtra(ScannerActivity.EXTRA_MEDICINE_NAME) ?: ""
        imageUrl = intent.getStringExtra(ScannerActivity.EXTRA_MEDICINE_IMAGE) ?: ""

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Alarm Kur"

        // Default değerler
        setDefaultValues()

        // İLAÇ ADINI OTOMATIK DOLDUR
        if (medicineName.isNotEmpty()) {
            binding.etMedicineName.setText(medicineName)
            binding.etMedicineName.setSelection(medicineName.length)  // Cursor'u sona al
            Log.d("AlarmSetup", "✅ İlaç adı otomatik dolduruldu: $medicineName")
        }

        // BARKODU GÖSTER
        if (barcode.isNotEmpty()) {
            binding.tvBarcodeInfo.text = "Barkod: $barcode"
        }

        // Click listeners
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Default değerleri ayarla
     */
    private fun setDefaultValues() {
        // Şu anki saat
        val calendar = Calendar.getInstance()
        selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
        selectedMinute = calendar.get(Calendar.MINUTE)

        updateStartTimeText()

        // Barkod bilgisini göster
        if (barcode.isNotEmpty()) {
            binding.tvBarcodeInfo.text = "Barkod: $barcode"
        }
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Başlangıç saati seçimi
        binding.btnSelectStartTime.setOnClickListener {
            showTimePicker()
        }

        // Kaydet butonu
        binding.btnSave.setOnClickListener {
            validateAndSave()
        }

        // İptal butonu
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    /**
     * Saat seçici göster
     */
    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateStartTimeText()
            },
            selectedHour,
            selectedMinute,
            true // 24 saat formatı
        ).show()
    }

    /**
     * Seçilen saati text'e yaz
     */
    private fun updateStartTimeText() {
        val timeText = String.format("%02d:%02d", selectedHour, selectedMinute)
        binding.tvStartTime.text = timeText
    }

    /**
     * Validate ve kaydet
     */
    private fun validateAndSave() {
        // İlaç adı
        val name = binding.etMedicineName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etMedicineName.error = "İlaç adı gerekli"
            binding.etMedicineName.requestFocus()
            return
        }

        // Dozaj
        val dosage = binding.etDosage.text.toString().trim()
        if (dosage.isEmpty()) {
            binding.etDosage.error = "Dozaj bilgisi gerekli (örn: 500mg)"
            binding.etDosage.requestFocus()
            return
        }

        // Günde kaç defa
        val timesPerDayStr = binding.etTimesPerDay.text.toString().trim()
        if (timesPerDayStr.isEmpty()) {
            binding.etTimesPerDay.error = "Günde kaç defa kullanılacağını girin"
            binding.etTimesPerDay.requestFocus()
            return
        }
        val timesPerDay = timesPerDayStr.toIntOrNull()
        if (timesPerDay == null || timesPerDay < 1 || timesPerDay > 10) {
            binding.etTimesPerDay.error = "1-10 arası bir sayı girin"
            binding.etTimesPerDay.requestFocus()
            return
        }

        // Kaç saat aralıkla
        val intervalHoursStr = binding.etIntervalHours.text.toString().trim()
        if (intervalHoursStr.isEmpty()) {
            binding.etIntervalHours.error = "Saat aralığını girin"
            binding.etIntervalHours.requestFocus()
            return
        }
        val intervalHours = intervalHoursStr.toIntOrNull()
        if (intervalHours == null || intervalHours < 1 || intervalHours > 24) {
            binding.etIntervalHours.error = "1-24 arası bir sayı girin"
            binding.etIntervalHours.requestFocus()
            return
        }

        // Kaç gün kullanılacak
        val durationDaysStr = binding.etDurationDays.text.toString().trim()
        if (durationDaysStr.isEmpty()) {
            binding.etDurationDays.error = "Kaç gün kullanılacağını girin"
            binding.etDurationDays.requestFocus()
            return
        }
        val durationDays = durationDaysStr.toIntOrNull()
        if (durationDays == null || durationDays < 1 || durationDays > 365) {
            binding.etDurationDays.error = "1-365 arası bir sayı girin"
            binding.etDurationDays.requestFocus()
            return
        }

        // İlaç açıklaması (opsiyonel)
        val description = binding.etDescription.text.toString().trim()

        // Kaydet
        saveMedicineAndAlarms(
            name = name,
            dosage = dosage,
            timesPerDay = timesPerDay,
            intervalHours = intervalHours,
            durationDays = durationDays,
            description = description
        )
    }

    /**
     * İlaç ve alarmları kaydet
     */
    private fun saveMedicineAndAlarms(
        name: String,
        dosage: String,
        timesPerDay: Int,
        intervalHours: Int,
        durationDays: Int,
        description: String
    ) {
        // Loading göster
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Kaydediliyor..."

        val userId = repository.getCurrentUser()?.uid
        Log.d("AlarmSetup", "Current UserId: $userId")


        if (userId == null) {
            runOnUiThread {
                Toast.makeText(this, "Kullanıcı oturumu bulunamadı", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        lifecycleScope.launch {
            try {
                // Medicine objesini oluştur
                val startDate = System.currentTimeMillis()
                val startTime = String.format("%02d:%02d", selectedHour, selectedMinute)

                val medicine = Medicine(
                    id = "",
                    barcode = barcode,
                    name = name,
                    dosage = dosage,
                    imageUrl = imageUrl,
                    description = description,
                    timesPerDay = timesPerDay,
                    startTime = startTime,
                    intervalHours = intervalHours,
                    durationDays = durationDays,
                    startDate = startDate,
                    userId = userId,
                    createdAt = 0L,
                    active = true  // DEĞİŞTİ
                )
                Log.d("AlarmSetup", "Medicine to save: $medicine")

                // Firebase'e kaydet
                val medicineResult = repository.addMedicine(medicine)
                Log.d("AlarmSetup", "Save result: ${medicineResult.isSuccess}")
                Log.d("AlarmSetup", "Medicine ID: ${medicineResult.getOrNull()}")

                if (medicineResult.isFailure) {
                    val error = medicineResult.exceptionOrNull()
                    runOnUiThread {
                        Toast.makeText(
                            this@AlarmSetupActivity,
                            "İlaç kaydedilemedi: ${error?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = "Kaydet"
                    }
                    return@launch
                }

                val medicineId = medicineResult.getOrNull() ?: ""
                if (medicineId.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AlarmSetupActivity,
                            "İlaç kaydedilemedi: ID alınamadı",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = "Kaydet"
                    }
                    return@launch
                }

                // Alarmları oluştur ve kaydet
                val alarms = createAlarms(medicineId, name, timesPerDay, intervalHours, userId)

                var alarmsSaved = 0
                for (alarm in alarms) {
                    val alarmResult = repository.addAlarm(alarm)
                    if (alarmResult.isSuccess) {
                        // Android AlarmManager'a da ekle
                        AlarmScheduler.scheduleAlarm(this@AlarmSetupActivity, alarm)
                        alarmsSaved++
                    }
                }

                runOnUiThread {
                    if (alarmsSaved == alarms.size) {
                        Toast.makeText(
                            this@AlarmSetupActivity,
                            "İlaç ve ${alarmsSaved} alarm başarıyla kaydedildi!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToMain()
                    } else {
                        Toast.makeText(
                            this@AlarmSetupActivity,
                            "İlaç kaydedildi ama bazı alarmlar kaydedilemedi",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToMain()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@AlarmSetupActivity,
                        "Hata: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Kaydet"
                }
            }
        }
    }

    /**
     * Alarmları oluştur
     */
    private fun createAlarms(
        medicineId: String,
        medicineName: String,
        timesPerDay: Int,
        intervalHours: Int,
        userId: String
    ): List<Alarm> {
        val alarms = mutableListOf<Alarm>()
        val calendar = Calendar.getInstance()

        // Başlangıç saatini ayarla
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
        calendar.set(Calendar.MINUTE, selectedMinute)
        calendar.set(Calendar.SECOND, 0)

        for (i in 0 until timesPerDay) {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeString = String.format("%02d:%02d", hour, minute)

            val alarm = Alarm(
                medicineId = medicineId,
                medicineName = medicineName,
                userId = userId,
                hour = hour,
                minute = minute,
                timeString = timeString,
                requestCode = generateRequestCode(medicineId, i)
            )

            alarms.add(alarm)

            // Sonraki alarm için saat ekle
            calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
        }

        return alarms
    }

    /**
     * Unique request code oluştur (AlarmManager için)
     */
    private fun generateRequestCode(medicineId: String, index: Int): Int {
        return (medicineId.hashCode() + index) and 0x7FFFFFFF
    }

    /**
     * Ana ekrana git
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
}