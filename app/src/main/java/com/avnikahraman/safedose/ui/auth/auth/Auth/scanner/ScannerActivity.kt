package com.avnikahraman.safedose.ui.auth.auth.Auth.scanner

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
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.alarm.AlarmSetupActivity
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

    private var isScanning = true // Sürekli tarama yapmasın diye flag
    private var lastScannedCode: String? = null // Son taranan kod

    companion object {
        private const val TAG = "ScannerActivity"
        const val EXTRA_BARCODE = "extra_barcode"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
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
        // Taranan kodu göster
        binding.tvScannedCode.text = "Taranan Kod: $barcode"

        // Ses çal (opsiyonel - basit beep)
        // TODO: Beep sesi eklenebilir

        // Popup göster: "Alarm kurmak ister misiniz?"
        showAlarmSetupDialog(barcode)
    }

    /**
     * Alarm kurma dialogu göster
     */
    private fun showAlarmSetupDialog(barcode: String) {
        AlertDialog.Builder(this)
            .setTitle("İlaç Tarandı")
            .setMessage("Bu ilaç için alarm kurmak ister misiniz?\n\nBarkod: $barcode")
            .setPositiveButton("Evet") { _, _ ->
                // AlarmSetupActivity'ye git
                navigateToAlarmSetup(barcode)
            }
            .setNegativeButton("Hayır") { _, _ ->
                // Sadece kapat ve taramaya devam et
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
    private fun navigateToAlarmSetup(barcode: String) {
        // Önce bu ilaç daha önce taranmış mı kontrol et
        lifecycleScope.launch {
            val userId = repository.getCurrentUser()?.uid ?: return@launch
            val result = repository.getMedicineByBarcode(barcode, userId)

            if (result.isSuccess) {
                val existingMedicine = result.getOrNull()
                if (existingMedicine != null) {
                    // İlaç zaten var, kullanıcıya bildir
                    runOnUiThread {
                        showMedicineExistsDialog(existingMedicine.name)
                    }
                    return@launch
                }
            }

            // İlaç yok, alarm kurma ekranına git
            val intent = Intent(this@ScannerActivity, AlarmSetupActivity::class.java).apply {
                putExtra(EXTRA_BARCODE, barcode)
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