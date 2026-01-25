package com.avnikahraman.safedose

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.avnikahraman.safedose.databinding.ActivityMainBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.alarm.AlarmActivity
import com.avnikahraman.safedose.ui.auth.LoginActivity
import com.avnikahraman.safedose.ui.auth.manual.UserManualActivity
import com.avnikahraman.safedose.ui.medicines.MedicinesActivity
import com.google.android.material.snackbar.Snackbar
import com.avnikahraman.safedose.ui.auth.scanner.ScannerActivity
import com.avnikahraman.safedose.ui.main.DeveloperInfoActivity
import com.avnikahraman.safedose.ui.reports.ReportsActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: FirebaseRepository

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openScanner()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Bildirim izni verildi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        if (!repository.isUserLoggedIn()) {
            navigateToLogin()
            return
        }

        binding.btnUserManual.setOnClickListener {
            startActivity(Intent(this, UserManualActivity::class.java))
        }

        val user = repository.getCurrentUser()
        if (user?.isEmailVerified == false) {
            showEmailVerificationRequired()
            return
        }

        binding.btnDeveloperInfo.setOnClickListener {
            val intent = Intent(this, DeveloperInfoActivity::class.java)
            startActivity(intent)
        }

        copyManualPdfIfNotExists()
        checkNotificationPermission()
        checkFullScreenIntentPermission() // YENİ: Full-screen intent izni kontrolü
        setupClickListeners()
        displayUserInfo()
    }

    /**
     * Full-Screen Intent iznini kontrol et (Android 14+)
     */
    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (!notificationManager.canUseFullScreenIntent()) {
                // Kullanıcıya bir kez sor
                val sharedPrefs = getSharedPreferences("SafeDosePrefs", Context.MODE_PRIVATE)
                val askedBefore = sharedPrefs.getBoolean("full_screen_intent_asked", false)

                if (!askedBefore) {
                    showFullScreenIntentPermissionDialog()
                    sharedPrefs.edit().putBoolean("full_screen_intent_asked", true).apply()
                }
            }
        }
    }

    /**
     * Full-Screen Intent izni için dialog göster
     */
    private fun showFullScreenIntentPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Alarm İzni Gerekli")
            .setMessage("Alarmların ekran kilidi üstünde çalışması için 'Tam ekran bildirimler' iznini vermeniz gerekiyor.\n\nBu izin olmadan alarm çaldığında bildirimi manuel olarak açmanız gerekecek.")
            .setPositiveButton("Ayarlara Git") { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Daha Sonra", null)
            .show()
    }

    private fun copyManualPdfIfNotExists() {
        val pdfFile = File(filesDir, "manuel.pdf")
        if (!pdfFile.exists()) {
            assets.open("manuel.pdf").use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun setupClickListeners() {
        // QR/Barkod Tarama
        binding.btnScanQR.setOnClickListener {
            checkCameraPermissionAndOpenScanner()
        }

        // İlaçlarım
        binding.btnMyMedicines.setOnClickListener {
            val intent = Intent(this, MedicinesActivity::class.java)
            startActivity(intent)
        }

        // Raporlar
        binding.btnReports.setOnClickListener {
            val intent = Intent(this, ReportsActivity::class.java)
            startActivity(intent)
        }

        // Çıkış Yap
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun displayUserInfo() {
        val user = repository.getCurrentUser()
        user?.let {
            val displayName = it.displayName ?: it.email?.substringBefore("@") ?: "Kullanıcı"
            binding.tvWelcome.text = "Merhaba, $displayName!"
        }
    }

    private fun checkCameraPermissionAndOpenScanner() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openScanner()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    override fun onStart() {
        super.onStart()

        val prefs = getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
        val isAlarmActive = prefs.getBoolean("alarm_active_default", false)

        if (isAlarmActive) {
            val intent = Intent(this, AlarmActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivity(intent)
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Kamera İzni Gerekli")
            .setMessage("QR kod ve barkod taramak için kamera iznine ihtiyacımız var.")
            .setPositiveButton("İzin Ver") { _, _ ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Kamera İzni Reddedildi")
            .setMessage("QR kod ve barkod taramak için kamera iznine ihtiyacımız var. Lütfen ayarlardan izin verin.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Çıkış Yap")
            .setMessage("Çıkış yapmak istediğinizden emin misiniz?")
            .setPositiveButton("Evet") { _, _ ->
                logout()
            }
            .setNegativeButton("Hayır", null)
            .show()
    }

    private fun showEmailVerificationRequired() {
        AlertDialog.Builder(this)
            .setTitle("Email Doğrulama Gerekli")
            .setMessage("Uygulamayı kullanabilmek için email adresinizi doğrulamanız gerekiyor.")
            .setPositiveButton("Tamam") { _, _ ->
                repository.signOut()
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
    }

    private fun logout() {
        repository.signOut()
        Toast.makeText(this, "Çıkış yapıldı", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}