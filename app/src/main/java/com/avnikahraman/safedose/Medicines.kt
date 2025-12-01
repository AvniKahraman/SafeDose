package com.avnikahraman.safedose.ui.medicines

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.avnikahraman.safedose.databinding.ActivityMedicinesBinding
import com.avnikahraman.safedose.adapters.MedicineAdapter
import com.avnikahraman.safedose.models.Medicine
import com.avnikahraman.safedose.repository.FirebaseRepository
import kotlinx.coroutines.launch

/**
 * İlaç listesi ekranı
 * Kullanıcının kaydettiği ilaçları gösterir
 */
class MedicinesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicinesBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: MedicineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMedicinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "İlaçlarım"

        // RecyclerView setup
        setupRecyclerView()

        // İlaçları yükle
        loadMedicines()

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            loadMedicines()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Ekrana her dönüldüğünde listeyi yenile
        loadMedicines()
    }

    /**
     * RecyclerView kurulumu
     */
    private fun setupRecyclerView() {
        adapter = MedicineAdapter { medicine ->
            // İlaç tıklandığında detay ekranına git
            openMedicineDetail(medicine)
        }

        binding.recyclerViewMedicines.apply {
            layoutManager = LinearLayoutManager(this@MedicinesActivity)
            adapter = this@MedicinesActivity.adapter
            setHasFixedSize(true)
        }
    }

    /**
     * İlaçları yükle
     */
    private fun loadMedicines() {
        val userId = repository.getCurrentUser()?.uid
        if (userId == null) {
            Toast.makeText(this, "Kullanıcı oturumu bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Loading göster
        showLoading(true)

        lifecycleScope.launch {
            val result = repository.getUserMedicines(userId)

            runOnUiThread {
                showLoading(false)

                if (result.isSuccess) {
                    val medicines = result.getOrNull() ?: emptyList()

                    if (medicines.isEmpty()) {
                        showEmptyState()
                    } else {
                        showMedicineList(medicines)
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "İlaçlar yüklenemedi"
                    Toast.makeText(this@MedicinesActivity, error, Toast.LENGTH_LONG).show()
                    showEmptyState()
                }
            }
        }
    }

    /**
     * Loading durumunu göster/gizle
     */
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewMedicines.visibility = if (show) View.GONE else View.VISIBLE
    }

    /**
     * İlaç listesini göster
     */
    private fun showMedicineList(medicines: List<Medicine>) {
        binding.recyclerViewMedicines.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE
        adapter.submitList(medicines)
    }

    /**
     * Boş liste durumunu göster
     */
    private fun showEmptyState() {
        binding.recyclerViewMedicines.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
    }

    /**
     * İlaç detay ekranını aç
     */
    private fun openMedicineDetail(medicine: Medicine) {
        val intent = Intent(this, MedicineDetailActivity::class.java).apply {
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_ID, medicine.id)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_NAME, medicine.name)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_DOSAGE, medicine.dosage)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_DESCRIPTION, medicine.description)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_BARCODE, medicine.barcode)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_TIMES_PER_DAY, medicine.timesPerDay)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_START_TIME, medicine.startTime)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_INTERVAL_HOURS, medicine.intervalHours)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_DURATION_DAYS, medicine.durationDays)
            putExtra(MedicineDetailActivity.EXTRA_MEDICINE_START_DATE, medicine.startDate)
        }
        startActivity(intent)
    }
}