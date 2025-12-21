package com.avnikahraman.safedose.ui.reports

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.avnikahraman.safedose.databinding.ActivityReportsBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private lateinit var repository: FirebaseRepository
    private val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Raporlar"

        loadReport()

        binding.btnExportPdf.setOnClickListener {
            exportToPdf()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadReport() {
        val userId = repository.getCurrentUser()?.uid ?: return

        lifecycleScope.launch {
            val medicinesResult = repository.getUserMedicines(userId)

            if (medicinesResult.isSuccess) {
                val medicines = medicinesResult.getOrNull() ?: emptyList()

                val reportText = buildString {
                    append("📊 İLAÇ RAPORU\n")
                    append("═══════════════════════════\n\n")

                    if (medicines.isEmpty()) {
                        append("Henüz ilaç eklenmemiş.\n")
                    } else {
                        medicines.forEach { medicine ->
                            append("💊 ${medicine.name} (${medicine.dosage})\n")
                            append("   Başlangıç: ${dateFormat.format(Date(medicine.startDate))}\n")
                            append("   Süre: ${medicine.durationDays} gün\n")
                            append("   Günde: ${medicine.timesPerDay} kez\n")
                            append("   İlk doz saati: ${medicine.startTime}\n")
                            append("   Aralık: ${medicine.intervalHours} saat\n\n")
                        }
                    }
                }

                binding.tvReportContent.text = reportText
            }
        }
    }

    private fun exportToPdf() {
        val pdfDocument = PdfDocument()
        val paint = Paint()

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        paint.textSize = 12f
        val text = binding.tvReportContent.text.toString()
        val lines = text.split("\n")

        var y = 50f
        lines.forEach { line ->
            canvas.drawText(line, 50f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SafeDose_Rapor_${System.currentTimeMillis()}.pdf")

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "PDF kaydedildi: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF oluşturulamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }

        pdfDocument.close()
    }
}