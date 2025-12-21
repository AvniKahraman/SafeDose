package com.avnikahraman.safedose.ui.auth.scanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.BuildConfig
import com.avnikahraman.safedose.databinding.ActivityScannerBinding
import com.avnikahraman.safedose.network.RetrofitClient
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.alarm.AlarmSetupActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var cameraExecutor: ExecutorService

    private var isScanning = true
    private var lastScannedCode: String? = null

    companion object {
        const val EXTRA_BARCODE = "extra_barcode"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_IMAGE = "extra_medicine_image"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "QR / Barkod Tara"

        startCamera()

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener {
            Toast.makeText(this, "Flash yakında", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(::processBarcodes))
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        if (!isScanning || barcodes.isEmpty()) return

        val code = barcodes.first().rawValue ?: return
        if (code == lastScannedCode) return

        lastScannedCode = code
        isScanning = false

        runOnUiThread {
            binding.tvScannedCode.text = "Taranan Kod: $code"
            fetchMedicineInfo(code)
        }
    }

    private fun fetchMedicineInfo(barcode: String) {
        lifecycleScope.launch {
            try {
                // 1️⃣ İLAÇ ADI
                val textRes = RetrofitClient.googleSearchApi.search(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    searchEngineId = BuildConfig.GOOGLE_SEARCH_ENGINE_ID,
                    query = "$barcode ilaç"
                )

                val title = textRes.body()?.items?.firstOrNull()?.title ?: ""
                val medicineName = extractMedicineName(title)

                // 2️⃣ GÖRSEL – SERT
                val imageRes = RetrofitClient.googleSearchApi.searchImage(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    searchEngineId = BuildConfig.GOOGLE_SEARCH_ENGINE_ID,
                    query = "\"$medicineName\" ilaç kutusu blister -iphone -samsung -telefon -phone -case -cover -reklam\"",
                    num = 5
                )

                var imageUrl = pickStrictImage(imageRes.body()?.items)

                // 3️⃣ FALLBACK – YUMUŞAK
                if (imageUrl.isEmpty()) {
                    val fallback = RetrofitClient.googleSearchApi.searchImage(
                        apiKey = BuildConfig.GOOGLE_API_KEY,
                        searchEngineId = BuildConfig.GOOGLE_SEARCH_ENGINE_ID,
                        query = "$medicineName ilaç",
                        num = 5
                    )
                    imageUrl = pickLooseImage(fallback.body()?.items)
                }

                runOnUiThread {
                    showAlarmSetupDialog(barcode, medicineName, imageUrl)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showAlarmSetupDialog(barcode, "", "")
                }
            }
        }
    }

    private fun extractMedicineName(title: String): String {
        return title
            .substringBefore("-")
            .substringBefore("|")
            .substringBefore(":")
            .substringBefore("Fiyatı")
            .substringBefore("Nedir")
            .trim()
            .split(" ")
            .take(3)
            .joinToString(" ")
            .ifEmpty { title.take(40) }
    }

    private fun pickStrictImage(items: List<com.avnikahraman.safedose.models.GoogleSearchItem>?): String {
        if (items.isNullOrEmpty()) return ""
        for (item in items) {
            val link = item.link?.lowercase() ?: continue
            if ((link.endsWith(".jpg") || link.endsWith(".png")) &&
                !link.contains("iphone") &&
                !link.contains("samsung") &&
                !link.contains("phone") &&
                !link.contains("case") &&
                !link.contains("cover") &&
                !link.contains("stock") &&
                !link.contains("shutterstock") &&
                !link.contains("istock") &&
                !link.contains("alamy")
            ) return item.link!!
        }
        return ""
    }

    private fun pickLooseImage(items: List<com.avnikahraman.safedose.models.GoogleSearchItem>?): String {
        if (items.isNullOrEmpty()) return ""
        return items.firstNotNullOfOrNull { it.link } ?: ""
    }

    private fun showAlarmSetupDialog(barcode: String, name: String, image: String) {
        AlertDialog.Builder(this)
            .setTitle("İlaç Tarandı")
            .setMessage("İlaç: $name\n\nBarkod: $barcode\n\nAlarm kurmak ister misiniz?")
            .setPositiveButton("Evet") { _, _ ->
                navigateToAlarmSetup(barcode, name, image)
            }
            .setNegativeButton("Hayır") { _, _ -> resetScanning() }
            .setOnCancelListener { resetScanning() }
            .show()
    }

    private fun navigateToAlarmSetup(barcode: String, name: String, image: String) {
        lifecycleScope.launch {
            val userId = repository.getCurrentUser()?.uid ?: return@launch
            val exists = repository.getMedicineByBarcode(barcode, userId).getOrNull()

            if (exists != null) {
                showMedicineExistsDialog(exists.name)
                return@launch
            }

            startActivity(
                Intent(this@ScannerActivity, AlarmSetupActivity::class.java).apply {
                    putExtra(EXTRA_BARCODE, barcode)
                    putExtra(EXTRA_MEDICINE_NAME, name)
                    putExtra(EXTRA_MEDICINE_IMAGE, image)
                }
            )
            finish()
        }
    }

    private fun showMedicineExistsDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Zaten Kayıtlı")
            .setMessage("$name zaten kayıtlı.")
            .setPositiveButton("Tamam") { _, _ -> finish() }
            .show()
    }

    private fun resetScanning() {
        isScanning = true
        lastScannedCode = null
        binding.tvScannedCode.text = "Kamerayı barkoda tut"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class BarcodeAnalyzer(
        val onDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val img = imageProxy.image
            if (img != null) {
                scanner.process(
                    InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)
                ).addOnSuccessListener {
                    if (it.isNotEmpty()) onDetected(it)
                }.addOnCompleteListener {
                    imageProxy.close()
                }
            } else imageProxy.close()
        }
    }
}
