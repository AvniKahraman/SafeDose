package com.avnikahraman.safedose.ui.auth.manual

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.databinding.ActivityUserManualBinding
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector


class UserManualActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManualBinding
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val matrix = Matrix()
    private var scaleFactor = 1.0f


    private var currentPage = 0
    private val totalPages = 15 // manual_0 -> manual_14

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUserManualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Kullanım Kılavuzu"

        // İlk sayfa
        loadPage(currentPage)
        updateButtonText()

        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                loadPage(currentPage)
                updateButtonText()
            } else {
                // Son sayfa → Ana menü
                startActivity(Intent(this, MainActivity::class.java))
                finish()

            }
        }
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(1.0f, 4.0f)

                    matrix.setScale(scaleFactor, scaleFactor,
                        detector.focusX, detector.focusY)

                    binding.ivManual.imageMatrix = matrix
                    return true
                }
            }
        )

        binding.ivManual.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadPage(page: Int) {
        val inputStream = assets.open("manual/manual_$page.png")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        binding.ivManual.setImageBitmap(bitmap)
        inputStream.close()
    }

    private fun updateButtonText() {
        if (currentPage == totalPages - 1) {
            binding.btnNext.text = "Ana Menüye Dön"
        } else {
            binding.btnNext.text = "Sonraki"
        }
    }
}
