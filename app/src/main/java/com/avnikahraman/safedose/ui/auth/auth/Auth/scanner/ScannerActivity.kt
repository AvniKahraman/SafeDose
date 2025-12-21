package com.avnikahraman.safedose.ui.auth.scanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.databinding.ActivityScannerBinding
import com.avnikahraman.safedose.network.RetrofitClient
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.alarm.AlarmSetupActivity
import com.avnikahraman.safedose.BuildConfig  // ✅ BU SATIR OLMALI
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR ve Barkod tarama ekranı
 * ML Kit Barcode Scanning kullanır
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var cameraExecutor: ExecutorService

    private var isScanning = true
    private var lastScannedCode: String? = null // Son taranan kod

    companion object {
        private const val TAG = "ScannerActivity"
        const val EXTRA_BARCODE = "extra_barcode"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_MEDICINE_IMAGE = "extra_medicine_image"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.Companion.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "QR/Barkod Tara"

        // Kamerayı başlat
        startCamera()

        // Close button
        binding.btnClose.setOnClickListener {
            finish()
        }

        // Flash toggle (opsiyonel - basit bir toggle)
        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Kamerayı başlat
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Image Analysis (ML Kit için)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodes ->
                        processBarcodes(barcodes)
                    })
                }

            // Arka kamera seç
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Önce tüm use case'leri unbind et
                cameraProvider.unbindAll()

                // Use case'leri bind et
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (e: Exception) {
                Log.e(TAG, "Kamera başlatma hatası", e)
                Toast.makeText(this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Barkodları işle
     */
    private fun processBarcodes(barcodes: List<Barcode>) {
        if (!isScanning || barcodes.isEmpty()) return

        for (barcode in barcodes) {
            val rawValue = barcode.rawValue ?: continue

            // Aynı kodu tekrar tekrar taramasın
            if (rawValue == lastScannedCode) continue

            lastScannedCode = rawValue
            isScanning = false // Taramayı durdur

            runOnUiThread {
                onBarcodeScanned(rawValue)
            }
            break
        }
    }

    /**
     * Barkod tarandığında
     */
    private fun onBarcodeScanned(barcode: String) {
        binding.tvScannedCode.text = "Taranan Kod: $barcode"

        // API'den ilaç bilgisi çek
        fetchMedicineInfo(barcode)
    }
    private fun fetchMedicineInfo(barcode: String) {
        lifecycleScope.launch {
            try {
                // 1️⃣ METİN ARAMASI (İLACABAK vs – İSİM İÇİN)
                val textResponse = RetrofitClient.googleSearchApi.search(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    searchEngineId = BuildConfig.GOOGLE_SEARCH_ENGINE_ID,
                    query = "$barcode ilaç"
                )

                val firstTextItem = textResponse.body()
                    ?.items
                    ?.firstOrNull()

                val medicineName = extractMedicineName(firstTextItem?.title ?: "")

                // 2️⃣ GÖRSEL ARAMASI (SADECE IMAGE SEARCH)
                val imageResponse = RetrofitClient.googleSearchApi.searchImage(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    searchEngineId = BuildConfig.GOOGLE_SEARCH_ENGINE_ID,
                    query = "$medicineName ilaç kutusu  ambalaj -reklam -stock -site:ilacabak.com -site:ilacrehberi.com "
                )

                val imageUrl = pickValidImage(imageResponse.body()?.items)


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
    private fun pickValidImage(items: List<com.avnikahraman.safedose.models.GoogleSearchItem>?): String {
        if (items.isNullOrEmpty()) return ""

        for (item in items) {
            val link = item.link ?: continue
            if (blockedImageDomains.none { link.contains(it, ignoreCase = true) }) {
                return link
            }
        }
        return ""
    }

    private val blockedImageDomains = listOf(
        "googleusercontent.com",
        "gstatic.com",
        "ads",
        "reklam",
        "stock",
        "shutterstock",
        "istock",
        "alamy",
        "depositphotos"
    )


    private fun extractMedicineName(title: String): String {
        // "Aspirin 500mg - İlaç Prospektüsü" -> "Aspirin 500mg"
        return title
            .substringBefore("-")
            .substringBefore("|")
            .substringBefore(":")
            .substringBefore("Fiyatı")
            .substringBefore("Nedir")
            .trim()
            .split(" ")
            .take(2)
            .joinToString(" ")
            .ifEmpty { title.take(50) }
    }



    /**
     * Alarm kurma dialogu göster
     */
    private fun showAlarmSetupDialog(barcode: String, medicineName: String, imageUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("İlaç Tarandı")
            .setMessage(
                if (medicineName.isNotEmpty())
                    "İlaç: $medicineName\n\nBarkod: $barcode\n\nAlarm kurmak ister misiniz?"
                else
                    "Barkod: $barcode\n\nAlarm kurmak ister misiniz?"
            )
            .setPositiveButton("Evet") { _, _ ->
                navigateToAlarmSetup(barcode, medicineName, imageUrl)
            }
            .setNegativeButton("Hayır") { _, _ ->
                resetScanning()
            }
            .setOnCancelListener {
                resetScanning()
            }
            .show()
    }

    /**
     * Alarm kurma ekranına git
     */
    private fun navigateToAlarmSetup(barcode: String, medicineName: String, imageUrl: String) {
        lifecycleScope.launch {
            val userId = repository.getCurrentUser()?.uid ?: return@launch
            val result = repository.getMedicineByBarcode(barcode, userId)

            if (result.isSuccess) {
                val existingMedicine = result.getOrNull()
                if (existingMedicine != null) {
                    runOnUiThread {
                        showMedicineExistsDialog(existingMedicine.name)
                    }
                    return@launch
                }
            }

            val intent = Intent(this@ScannerActivity, AlarmSetupActivity::class.java).apply {
                putExtra(EXTRA_BARCODE, barcode)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_MEDICINE_IMAGE, imageUrl)
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * İlaç zaten varsa göster
     */
    private fun showMedicineExistsDialog(medicineName: String) {
        AlertDialog.Builder(this)
            .setTitle("İlaç Zaten Mevcut")
            .setMessage("Bu ilaç ($medicineName) zaten kayıtlı. İlaçlarım bölümünden görüntüleyebilirsiniz.")
            .setPositiveButton("Tamam") { _, _ ->
                finish()
            }
            .show()
    }

    /**
     * Taramayı sıfırla (tekrar tarama yapabilmek için)
     */
    private fun resetScanning() {
        isScanning = true
        lastScannedCode = null
        binding.tvScannedCode.text = "Kamerayı QR veya Barkoda tutun"
    }

    /**
     * Flash toggle (basit implementasyon)
     */
    private fun toggleFlash() {
        // TODO: Flash toggle implementasyonu eklenebilir
        Toast.makeText(this, "Flash özelliği yakında eklenecek", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /**
     * ML Kit Barcode Analyzer
     */
    private class BarcodeAnalyzer(
        private val onBarcodesDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            onBarcodesDetected(barcodes)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barkod tarama hatası", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}