package com.example.nativemap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.nativemap.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
