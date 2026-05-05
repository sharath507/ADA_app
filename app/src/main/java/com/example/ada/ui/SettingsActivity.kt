package com.example.ada.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ada.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Backend Settings"
            textSize = 18f
        }

        val urlInput = EditText(this).apply {
            hint = "http://192.168.1.5:8000"
        }

        val saveBtn = Button(this).apply {
            text = "Save"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(urlInput)
            addView(saveBtn)
        }

        setContentView(root)

        lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) {
                BackendConfig.appConfig(applicationContext).getBackendUrlOnce(BackendConfig.defaultUrl())
            }
            urlInput.setText(current)
        }

        saveBtn.setOnClickListener {
            val raw = urlInput.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    BackendConfig.appConfig(applicationContext).setBackendUrl(raw)
                }
                finish()
            }
        }
    }
}
