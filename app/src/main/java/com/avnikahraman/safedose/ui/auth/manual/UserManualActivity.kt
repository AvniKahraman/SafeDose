package com.avnikahraman.safedose.ui.auth.manual

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.app.AppCompatActivity
import com.avnikahraman.safedose.MainActivity
import com.avnikahraman.safedose.databinding.ActivityUserManualBinding

class UserManualActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManualBinding

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val matrix = Matrix()

    private var scaleFactor = 1.0f
    private var lastX = 0f
    private var lastY = 0f

    private var currentPage = 0
    private val totalPages = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUserManualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Kullanım Kılavuzu"

        loadPage(currentPage)
        updateButtonText()

        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                resetZoom()
                loadPage(currentPage)
                updateButtonText()
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        // SCALE (PINCH ZOOM)
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(1.0f, 4.0f)

                    matrix.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY
                    )

                    binding.ivManual.imageMatrix = matrix
                    return true
                }
            })

        // TOUCH (PAN + SCALE)
        binding.ivManual.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        matrix.postTranslate(dx, dy)
                        binding.ivManual.imageMatrix = matrix
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
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

    private fun resetZoom() {
        matrix.reset()
        scaleFactor = 1.0f
        binding.ivManual.imageMatrix = matrix
    }

    private fun updateButtonText() {
        binding.btnNext.text =
            if (currentPage == totalPages - 1) "Ana Menüye Dön" else "Sonraki"
    }
}
