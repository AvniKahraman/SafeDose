package com.avnikahraman.safedose.ui.medicines

import com.bumptech.glide.Glide
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.databinding.ActivityMedicineDetailBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.AlarmScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.avnikahraman.safedose.R


/**
 * İlaç detay ekranı
 * İlaç bilgilerini ve alarm detaylarını gösterir
 */
class MedicineDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicineDetailBinding
    private lateinit var repository: FirebaseRepository
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))

    private var medicineId: String = ""

    companion object {
        const val EXTRA_MEDICINE_ID = "medicine_id"
        const val EXTRA_MEDICINE_NAME = "medicine_name"
        const val EXTRA_MEDICINE_DOSAGE = "medicine_dosage"
        const val EXTRA_MEDICINE_DESCRIPTION = "medicine_description"
        const val EXTRA_MEDICINE_BARCODE = "medicine_barcode"
        const val EXTRA_MEDICINE_START_TIME = "medicine_start_time"
        const val EXTRA_MEDICINE_INTERVAL_HOURS = "medicine_interval_hours"
        const val EXTRA_MEDICINE_DURATION_DAYS = "medicine_duration_days"
        const val EXTRA_MEDICINE_START_DATE = "medicine_start_date"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMedicineDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        // Intent'ten verileri al
        medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: ""
        val name = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: ""
        val dosage = intent.getStringExtra(EXTRA_MEDICINE_DOSAGE) ?: ""
        val description = intent.getStringExtra(EXTRA_MEDICINE_DESCRIPTION) ?: ""
        val barcode = intent.getStringExtra(EXTRA_MEDICINE_BARCODE) ?: ""
        val timesPerDay = intent.getIntExtra(EXTRA_MEDICINE_INTERVAL_HOURS, 0)
        val startTime = intent.getStringExtra(EXTRA_MEDICINE_START_TIME) ?: ""
        val durationDays = intent.getIntExtra(EXTRA_MEDICINE_DURATION_DAYS, 0)
        val startDate = intent.getLongExtra(EXTRA_MEDICINE_START_DATE, 0L)

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = name

        // Verileri göster
        displayMedicineInfo(
            name, dosage, description, barcode,
            timesPerDay, startTime,
            durationDays, startDate
        )


        // Click listeners
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * İlaç bilgilerini göster
     */
    private fun displayMedicineInfo(
        name: String,
        dosage: String,
        description: String,
        barcode: String,
        timesPerDay: Int,
        startTime: String,
        durationDays: Int,
        startDate: Long
    )
    {
        binding.apply {

            // İlaç bilgileri
            tvMedicineName.text = name
            tvDosage.text = dosage
            tvDescription.text = if (description.isNotEmpty()) description else "Açıklama eklenmemiş"
            tvBarcode.text = barcode

            // Kullanım bilgileri
            tvStartTime.text = startTime
            // Kullanım bilgileri
            tvStartTime.text = startTime
            tvTimesPerDay.text = if (timesPerDay > 0) "Günde ${24 / timesPerDay} defa" else "Bilgi yok"
            tvDurationDays.text = "$durationDays gün"


            val imageUrl = intent.getStringExtra("EXTRA_MEDICINE_IMAGE") ?: ""
            if (imageUrl.isNotEmpty()) {
                Glide.with(this@MedicineDetailActivity)
                    .load(imageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(ivMedicineImage)
            } else {
                ivMedicineImage.setImageResource(R.drawable.ic_launcher_foreground)
            }

            // Tarih bilgileri
            val startDateStr = dateFormat.format(Date(startDate))
            tvStartDate.text = startDateStr

            val endDate = startDate + (durationDays * 24 * 60 * 60 * 1000L)
            val endDateStr = dateFormat.format(Date(endDate))
            tvEndDate.text = endDateStr

            // Kalan gün
            val daysRemaining = calculateDaysRemaining(endDate)
            if (daysRemaining > 0) {
                tvDaysRemaining.text = "$daysRemaining gün kaldı"
                tvDaysRemaining.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvDaysRemaining.text = "Tamamlandı"
                tvDaysRemaining.setTextColor(getColor(android.R.color.darker_gray))
            }

            // Alarm saatleri listesi
            val alarmTimes = calculateAlarmTimes(startTime, timesPerDay)
            tvAlarmTimes.text = "$timesPerDay defa içilecek"        }
    }
    private fun calculateDaysRemaining(endDate: Long): Int {
        val currentTime = System.currentTimeMillis()
        val remainingMillis = endDate - currentTime
        val remainingDays = (remainingMillis / (1000 * 60 * 60 * 24)).toInt()
        return maxOf(0, remainingDays)
    }

    /**
     * Kalan gün sayısını hesapla
     */
    private fun calculateAlarmTimes(
        startTime: String,
        timesPerDay: Int
    ): List<String> {

        if (timesPerDay <= 0) return emptyList()

        val times = mutableListOf<String>()

        val parts = startTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val intervalHours = 24 / timesPerDay

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        repeat(timesPerDay) {
            times.add(
                String.format(
                    "%02d:%02d",
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
            )
            calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
        }

        return times
    }

    /**
     * Alarm saatlerini hesapla
     */
    private fun calculateAlarmTimes(
        startTime: String,
        timesPerDay: Int,
        intervalHours: Int
    ): List<String> {
        val times = mutableListOf<String>()
        val parts = startTime.split(":")
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        for (i in 0 until timesPerDay) {
            val h = calendar.get(Calendar.HOUR_OF_DAY)
            val m = calendar.get(Calendar.MINUTE)
            times.add(String.format("%02d:%02d", h, m))
            calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
        }

        return times
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Sil butonu
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    /**
     * Silme onay dialogu
     */
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("İlacı Sil")
            .setMessage("Bu ilacı ve tüm alarmlarını silmek istediğinizden emin misiniz?")
            .setPositiveButton("Evet, Sil") { _, _ ->
                deleteMedicine()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * İlacı sil
     */
    private fun deleteMedicine() {
        binding.btnDelete.isEnabled = false
        binding.btnDelete.text = "Siliniyor..."

        lifecycleScope.launch {
            try {
                // ÖNCE ALARMLARI AL (isActive=true olanlar)
                val alarmsResult = repository.getMedicineAlarms(medicineId)
                val alarms = if (alarmsResult.isSuccess) {
                    alarmsResult.getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }

                // ALARMLARI ANDROID ALARMMANAGER'DAN SİL
                for (alarm in alarms) {
                    AlarmScheduler.cancelAlarm(this@MedicineDetailActivity, alarm)
                }

                // ŞİMDİ FIREBASE'DEN SİL (soft delete)
                val deleteMedicineResult = repository.deleteMedicine(medicineId)
                val deleteAlarmsResult = repository.deleteMedicineAlarms(medicineId)

                runOnUiThread {
                    if (deleteMedicineResult.isSuccess && deleteAlarmsResult.isSuccess) {
                        Toast.makeText(
                            this@MedicineDetailActivity,
                            "İlaç başarıyla silindi",
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(
                            this@MedicineDetailActivity,
                            "Silme işlemi başarısız",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.btnDelete.isEnabled = true
                        binding.btnDelete.text = "İlacı Sil"
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MedicineDetailActivity,
                        "Hata: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnDelete.isEnabled = true
                    binding.btnDelete.text = "İlacı Sil"
                }
            }
        }
    }
}