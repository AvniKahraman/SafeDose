package com.avnikahraman.safedose.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.R
import com.avnikahraman.safedose.databinding.ActivityLoginBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import kotlinx.coroutines.launch

/**
 * Kullanıcı giriş ekranı
 * Email/Şifre ve Google ile giriş destekler
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var googleSignInClient: GoogleSignInClient

    // Google Sign In için launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Binding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Repository instance
        repository = FirebaseRepository.getInstance()

        // Kullanıcı zaten giriş yapmışsa ana ekrana yönlendir
        if (repository.isUserLoggedIn()) {
            navigateToMainActivity()
            return
        }

        // Google Sign In yapılandırması
        setupGoogleSignIn()

        // Click listener'lar
        setupClickListeners()
    }

    /**
     * Google Sign In yapılandırması
     */
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Giriş yap butonu
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                signInWithEmail(email, password)
            }
        }

        // Kayıt ol text'i
        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Google ile giriş butonu
        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        // Şifremi unuttum
        binding.tvForgotPassword.setOnClickListener {
            // TODO: Şifre sıfırlama ekranı
            Toast.makeText(this, "Şifre sıfırlama özelliği yakında eklenecek", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Input validasyonu
     */
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.error = "Email gerekli"
            binding.etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Geçerli bir email girin"
            binding.etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Şifre gerekli"
            binding.etPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            binding.etPassword.error = "Şifre en az 6 karakter olmalı"
            binding.etPassword.requestFocus()
            return false
        }

        return true
    }

    /**
     * Email ve şifre ile giriş yap
     */
    private fun signInWithEmail(email: String, password: String) {
        // Loading göster
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Giriş yapılıyor..."

        lifecycleScope.launch {
            val result = repository.signInWithEmail(email, password)

            if (result.isSuccess) {
                Toast.makeText(this@LoginActivity, "Giriş başarılı!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            } else {
                // Hata göster
                val error = result.exceptionOrNull()?.message ?: "Giriş başarısız"
                Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()

                // Butonu tekrar aktif et
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Giriş Yap"
            }
        }
    }

    /**
     * Google ile giriş yap
     */
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    /**
     * Google Sign In sonucunu işle
     */
    private fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)

            // ID token al
            account?.idToken?.let { idToken ->
                firebaseAuthWithGoogle(idToken)
            } ?: run {
                Toast.makeText(this, "Google girişi başarısız", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google girişi iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Google ID token ile Firebase'e giriş yap
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Giriş yapılıyor..."

        lifecycleScope.launch {
            val result = repository.signInWithGoogle(idToken)

            if (result.isSuccess) {
                Toast.makeText(this@LoginActivity, "Google ile giriş başarılı!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Google girişi başarısız"
                Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()

                binding.btnGoogleSignIn.isEnabled = true
                binding.btnGoogleSignIn.text = "Google ile Giriş Yap"
            }
        }
    }

    /**
     * Ana ekrana git
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}