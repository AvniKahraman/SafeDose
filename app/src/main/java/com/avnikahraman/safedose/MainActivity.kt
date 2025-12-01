package com.avnikahraman.safedose

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.avnikahraman.safedose.databinding.ActivityMainBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.avnikahraman.safedose.ui.auth.auth.Auth.LoginActivity
import com.avnikahraman.safedose.ui.medicines.MedicinesActivity
import com.avnikahraman.safedose.ui.auth.auth.Auth.scanner.ScannerActivity

/**
 * Ana ekran - QR/Barkod tarama ve navigasyon
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: FirebaseRepository

    // Kamera izni için launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openScanner()
        } else {
            showPermissionDeniedDialog()
        }
    }

    // Bildirim izni için launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Bildirim izni verildi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Repository
        repository = FirebaseRepository.getInstance()

        // Kullanıcı giriş yapmamışsa login ekranına yönlendir
        if (!repository.isUserLoggedIn()) {
            navigateToLogin()
            return
        }

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "SafeDose"

        // Bildirim iznini kontrol et (Android 13+)
        checkNotificationPermission()

        // Click listener'lar
        setupClickListeners()

        // Kullanıcı bilgilerini göster
        displayUserInfo()
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Büyük QR/Barkod tarama butonu
        binding.btnScanQR.setOnClickListener {
            checkCameraPermissionAndOpenScanner()
        }

        // İlaçlarım butonu
        binding.btnMyMedicines.setOnClickListener {
            val intent = Intent(this, MedicinesActivity::class.java)
            startActivity(intent)
        }

        // Alarmlar butonu
        binding.btnAlarms.setOnClickListener {
            // TODO: Alarmlar ekranı (şimdilik toast göster)
            Toast.makeText(this, "Alarmlar ekranı yakında eklenecek", Toast.LENGTH_SHORT).show()
        }

        // Çıkış yap butonu (toolbar menüsünde olacak)
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    /**
     * Kullanıcı bilgilerini göster
     */
    private fun displayUserInfo() {
        val user = repository.getCurrentUser()
        user?.let {
            val displayName = it.displayName ?: it.email?.substringBefore("@") ?: "Kullanıcı"
            binding.tvWelcome.text = "Merhaba, $displayName!"
        }
    }

    /**
     * Kamera iznini kontrol et ve tarayıcıyı aç
     */
    private fun checkCameraPermissionAndOpenScanner() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // İzin var, tarayıcıyı aç
                openScanner()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // İzin reddedilmiş, açıklama göster
                showPermissionRationaleDialog()
            }
            else -> {
                // İzin iste
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Bildirim iznini kontrol et (Android 13+)
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * QR/Barkod tarayıcıyı aç
     */
    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivity(intent)
    }

    /**
     * Kamera izni açıklama dialogu
     */
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

    /**
     * Kamera izni reddedildi dialogu
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Kamera İzni Reddedildi")
            .setMessage("QR kod ve barkod taramak için kamera iznine ihtiyacımız var. Lütfen ayarlardan izin verin.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    /**
     * Çıkış yap dialogu
     */
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

    /**
     * Çıkış yap
     */
    private fun logout() {
        repository.signOut()
        Toast.makeText(this, "Çıkış yapıldı", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    /**
     * Login ekranına git
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}