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
import com.avnikahraman.safedose.databinding.ActivityRegisterBinding
import com.avnikahraman.safedose.repository.FirebaseRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
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

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FirebaseRepository.getInstance()

        setupGoogleSignIn()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Kayıt Ol"

        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (validateInput(name, email, password, confirmPassword)) {
                registerWithEmail(name, email, password)
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

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

    private fun registerWithEmail(name: String, email: String, password: String) {
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Kayıt yapılıyor..."

        lifecycleScope.launch {
            try {
                val result = repository.signUpWithEmail(email, password, name)

                runOnUiThread {
                    if (result.isSuccess) {
                        sendEmailVerification()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Kayıt başarısız"
                        Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = "Kayıt Ol"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Kayıt Ol"
                }
            }
        }
    }

    private fun sendEmailVerification() {
        val user = repository.getCurrentUser()

        user?.sendEmailVerification()?.addOnCompleteListener { task ->
            binding.btnRegister.isEnabled = true
            binding.btnRegister.text = "Kayıt Ol"

            if (task.isSuccessful) {
                showEmailVerificationDialog()
            } else {
                Toast.makeText(
                    this,
                    "Email gönderilemedi: ${task.exception?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEmailVerificationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Email Doğrulama")
            .setMessage("Kayıt başarılı! Email adresinize doğrulama linki gönderildi. Lütfen email'inizi kontrol edin ve doğrulama linkine tıklayın.\n\nDoğruladıktan sonra giriş yapabilirsiniz.")
            .setPositiveButton("Tamam") { _, _ ->
                repository.signOut()
                finish()
            }
            .setCancelable(false)
            .show()
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
                Toast.makeText(this, "Google kaydı başarısız", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google kaydı iptal edildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnGoogleSignIn.text = "Kayıt yapılıyor..."

        lifecycleScope.launch {
            try {
                val result = repository.signInWithGoogle(idToken)

                runOnUiThread {
                    if (result.isSuccess) {
                        Toast.makeText(this@RegisterActivity, "Google ile kayıt başarılı!", Toast.LENGTH_SHORT).show()
                        navigateToMainActivity()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Google kaydı başarısız"
                        Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                        binding.btnGoogleSignIn.isEnabled = true
                        binding.btnGoogleSignIn.text = "Google ile Kayıt Ol"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Google ile Kayıt Ol"
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