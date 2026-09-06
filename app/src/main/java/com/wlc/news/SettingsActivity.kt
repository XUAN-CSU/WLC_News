package com.wlc.news

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val etBasePath = findViewById<EditText>(R.id.etBasePath)
        val etSuffix = findViewById<EditText>(R.id.etSuffix)

        etHost.setText(Prefs.host(this))
        etPort.setText(Prefs.port(this).toString())
        etUser.setText(Prefs.user(this))
        etPass.setText(Prefs.password(this))
        etBasePath.setText(Prefs.basePath(this))
        etSuffix.setText(Prefs.dateSuffix(this))

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()
            val base = etBasePath.text.toString().trim()
            val suffix = etSuffix.text.toString().trim()
            if (host.isEmpty() || user.isEmpty() || base.isEmpty()) {
                Toast.makeText(this, "Host, username and folder are required", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.save(this, host, port, user, pass, base, suffix)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
