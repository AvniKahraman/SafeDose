package com.avnikahraman.safedose.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.R
import com.avnikahraman.safedose.databinding.ActivityLoginBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: FirebaseRepository
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        if (repository.isUserLoggedIn()) {
            navigateToMainActivity()
            return
        }

        setupGoogleSignIn()
        setupClickListeners()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                signInWithEmail(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Şifre sıfırlama özelliği yakında eklenecek", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
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

        return true
    }

    private fun signInWithEmail(email: String, password: String) {
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Giriş yapılıyor..."

        lifecycleScope.launch {
            try {
                val result = repository.signInWithEmail(email, password)

                runOnUiThread {
                    if (result.isSuccess) {
                        val user = repository.getCurrentUser()

                        if (user?.isEmailVerified == true) {
                            Toast.makeText(this@LoginActivity, "Giriş başarılı!", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        } else {
                            showEmailNotVerifiedDialog()
                        }
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Giriş başarısız"
                        Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Giriş Yap"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Giriş Yap"
                }
            }
        }
    }

    private fun showEmailNotVerifiedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Email Doğrulanmadı")
            .setMessage("Email adresiniz henüz doğrulanmadı. Lütfen email'inizdeki doğrulama linkine tıklayın.\n\nYeni doğrulama linki göndermek ister misiniz?")
            .setPositiveButton("Yeni Link Gönder") { _, _ ->
                resendVerificationEmail()
            }
            .setNegativeButton("İptal") { _, _ ->
                repository.signOut()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Giriş Yap"
            }
            .setCancelable(false)
            .show()
    }

    private fun resendVerificationEmail() {
        val user = repository.getCurrentUser()

        user?.sendEmailVerification()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Doğrulama email'i tekrar gönderildi!", Toast.LENGTH_SHORT).show()
                repository.signOut()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Giriş Yap"
            } else {
                Toast.makeText(this, "Email gönderilemedi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Giriş Yap"
            }
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)

            account?.idToken?.let { idToken ->
                firebaseAuthWithGoogle(idToken)
            } ?: run {
                Toast.makeText(this, "Google girişi başarısız", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google girişi iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Giriş yapılıyor..."

        lifecycleScope.launch {
            try {
                val result = repository.signInWithGoogle(idToken)

                runOnUiThread {
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
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Google ile Giriş Yap"
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}