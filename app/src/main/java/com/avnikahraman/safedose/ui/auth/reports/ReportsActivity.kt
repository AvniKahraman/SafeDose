package com.avnikahraman.safedose.ui.reports
import kotlinx.coroutines.runBlocking
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.R
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

                // Ekranda eski haliyle göster (alt alta)
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
                            append("Günde Kaç Defa: ${24 / medicine.intervalHours} defa\n")
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
        val userId = repository.getCurrentUser()?.uid ?: return File("")

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var y = 50f

        // Logo çiz
        try {
            val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.raport_logo)
            val logoWidth = 120f
            val logoHeight = 120f
            val logoX = (595 - logoWidth) / 2 // Ortalama
            canvas.drawBitmap(
                logoBitmap,
                null,
                android.graphics.RectF(logoX, y, logoX + logoWidth, y + logoHeight),
                null
            )
            y += logoHeight + 20f
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // "SafeDose" başlığı
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SafeDose", 297.5f, y, titlePaint)
        y += 30f

        // "İlaç Kullanım Raporu" alt başlık
        val subtitlePaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("İlaç Kullanım Raporu", 297.5f, y, subtitlePaint)
        y += 40f

        // Tablo için paint'ler
        val headerPaint = Paint().apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val normalPaint = Paint().apply {
            textSize = 8f
        }

        // İlaç verilerini al ve tablo oluştur
        val result = runBlocking { repository.getUserMedicines(userId) }

        if (result.isSuccess) {
            val medicines = result.getOrNull().orEmpty()

            if (medicines.isEmpty()) {
                canvas.drawText("Herhangi bir ilaç kaydı bulunmamaktadır.", 40f, y, normalPaint)
            } else {
                // Tablo başlıkları - kolonlar
                val cols = listOf(40f, 140f, 230f, 310f, 390f, 460f)
                val headers = listOf("İlaç Adı", "Dozaj", "Başlangıç", "Bitiş", "Günde", "İlk Doz")

                headers.forEachIndexed { index, header ->
                    canvas.drawText(header, cols[index], y, headerPaint)
                }
                y += 5f

                // Başlık çizgisi
                canvas.drawLine(40f, y, 540f, y, normalPaint)
                y += 15f

                // İlaç verileri
                medicines.forEach { medicine ->
                    val startDate = Date(medicine.startDate)
                    val endDate = Calendar.getInstance().apply {
                        time = startDate
                        add(Calendar.DAY_OF_YEAR, medicine.durationDays)
                    }.time

                    canvas.drawText(medicine.name.take(15), cols[0], y, normalPaint)
                    canvas.drawText(medicine.dosage.take(12), cols[1], y, normalPaint)
                    canvas.drawText(dateFormat.format(startDate), cols[2], y, normalPaint)
                    canvas.drawText(dateFormat.format(endDate), cols[3], y, normalPaint)
                    canvas.drawText(
                        (24 / medicine.intervalHours).toString(),
                        cols[4],
                        y,
                        normalPaint
                    )
                    canvas.drawText(
                        medicine.startTime,
                        cols[5],
                        y,
                        normalPaint
                    )


                    y += 18f
                }
            }
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

