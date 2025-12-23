package com.avnikahraman.safedose.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.avnikahraman.safedose.R
import com.avnikahraman.safedose.databinding.ActivityDeveloperInfoBinding

class DeveloperInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.developerToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.developerToolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
