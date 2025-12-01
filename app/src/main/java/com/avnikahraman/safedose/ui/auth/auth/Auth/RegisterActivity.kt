package com.avnikahraman.safedose.ui.auth.auth.Auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.R
import com.avnikahraman.safedose.databinding.ActivityRegisterBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch

/**
 * Kullanıcı kayıt ekranı
 * Email/Şifre ve Google ile kayıt destekler
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
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
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Repository instance
        repository = FirebaseRepository.Companion.getInstance()

        // Google Sign In yapılandırması
        setupGoogleSignIn()

        // Toolbar setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Kayıt Ol"

        // Click listener'lar
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
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
        // Kayıt ol butonu
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (validateInput(name, email, password, confirmPassword)) {
                registerWithEmail(name, email, password)
            }
        }

        // Giriş yap text'i
        binding.tvLogin.setOnClickListener {
            finish() // LoginActivity'ye geri dön
        }

        // Google ile kayıt ol butonu
        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

    /**
     * Input validasyonu
     */
    private fun validateInput(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (name.isEmpty()) {
            binding.etName.error = "İsim gerekli"
            binding.etName.requestFocus()
            return false
        }

        if (name.length < 2) {
            binding.etName.error = "İsim en az 2 karakter olmalı"
            binding.etName.requestFocus()
            return false
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Email gerekli"
            binding.etEmail.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Şifre tekrarı gerekli"
            binding.etConfirmPassword.requestFocus()
            return false
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Şifreler eşleşmiyor"
            binding.etConfirmPassword.requestFocus()
            return false
        }

        return true
    }

    /**
     * Email ve şifre ile kayıt ol
     */
    private fun registerWithEmail(name: String, email: String, password: String) {
        // Loading göster
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Kayıt yapılıyor..."

        lifecycleScope.launch {
            val result = repository.signUpWithEmail(email, password, name)

            if (result.isSuccess) {
                Toast.makeText(
                    this@RegisterActivity,
                    "Kayıt başarılı! Hoş geldiniz!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMainActivity()
            } else {
                // Hata göster
                val error = result.exceptionOrNull()?.message ?: "Kayıt başarısız"
                Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()

                // Butonu tekrar aktif et
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = "Kayıt Ol"
            }
        }
    }

    /**
     * Google ile kayıt ol
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
                Toast.makeText(this, "Google kaydı başarısız", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google kaydı iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Google ID token ile Firebase'e kayıt ol
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Kayıt yapılıyor..."

        lifecycleScope.launch {
            val result = repository.signInWithGoogle(idToken)

            if (result.isSuccess) {
                Toast.makeText(
                    this@RegisterActivity,
                    "Google ile kayıt başarılı!",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMainActivity()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Google kaydı başarısız"
                Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()

                binding.btnGoogleSignIn.isEnabled = true
                binding.btnGoogleSignIn.text = "Google ile Kayıt Ol"
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