package com.wlc.news

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton

class ChannelActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LANGUAGE = "language"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "English"
        findViewById<TextView>(R.id.tvHeading).text = when (language) {
            "English" -> "English channels"
            else -> "$language channels"
        }

        findViewById<MaterialButton>(R.id.btnNpr).setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnWapo).setOnClickListener {
            Toast.makeText(this, "The Washington Post — coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
