package com.avnikahraman.safedose.ui.reports

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
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
    private lateinit var pdfFile: File

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "İlaç Raporu"

        loadReport()

        binding.btnExportPdf.setOnClickListener {
            pdfFile = createPdf()
            openPdf(pdfFile)
        }

        binding.btnSendMail.setOnClickListener {
            if (!::pdfFile.isInitialized) {
                pdfFile = createPdf()
            }
            sendPdfByMail(pdfFile)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadReport() {
        val userId = repository.getCurrentUser()?.uid ?: return

        lifecycleScope.launch {
            val result = repository.getUserMedicines(userId)

            if (result.isSuccess) {
                val medicines = result.getOrNull().orEmpty()

                val reportText = buildString {
                    append("SAFE DOSE - İLAÇ KULLANIM RAPORU\n")
                    append("══════════════════════════════\n\n")

                    if (medicines.isEmpty()) {
                        append("Herhangi bir ilaç kaydı bulunmamaktadır.\n")
                    } else {
                        medicines.forEach { medicine ->
                            val startDate = Date(medicine.startDate)
                            val endDate = Calendar.getInstance().apply {
                                time = startDate
                                add(Calendar.DAY_OF_YEAR, medicine.durationDays)
                            }.time

                            append("İlaç Adı: ${medicine.name}\n")
                            append("Dozaj: ${medicine.dosage}\n")
                            append("Başlangıç Tarihi: ${dateFormat.format(startDate)}\n")
                            append("Bitiş Tarihi: ${dateFormat.format(endDate)}\n")
                            append("Günde Kullanım: ${medicine.timesPerDay} kez\n")
                            append("Kullanım Aralığı: ${medicine.intervalHours} saat\n")
                            append("İlk Doz Saati: ${medicine.startTime}\n")
                            append("----------------------------------\n\n")
                        }
                    }
                }

                binding.tvReportContent.text = reportText
            } else {
                Toast.makeText(this@ReportsActivity, "Rapor yüklenemedi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createPdf(): File {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply { textSize = 12f }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val lines = binding.tvReportContent.text.toString().split("\n")
        var y = 50f

        lines.forEach {
            canvas.drawText(it, 40f, y, paint)
            y += 18f
        }

        pdfDocument.finishPage(page)

        val file = File(
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "SafeDose_Ilac_Raporu.pdf"
        )

        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        return file
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(intent)
    }

    private fun sendPdfByMail(file: File) {
        val userEmail = repository.getCurrentUser()?.email ?: return

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(userEmail))
            putExtra(Intent.EXTRA_SUBJECT, "SafeDose - İlaç Kullanım Raporu")
            putExtra(Intent.EXTRA_TEXT, "İlaç kullanım raporunuz ekte PDF olarak yer almaktadır.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Mail uygulaması seç"))
    }
}
