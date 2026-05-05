package com.example.ada

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ada.service.AssistantService
import com.example.ada.state.AssistantBus
import com.example.ada.ui.JarvisLottieView
import com.example.ada.ui.SettingsActivity
import com.example.ada.vision.CameraVisionActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ada.config.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val audioPermissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val root = ScrollView(this).apply {
            addView(content)
        }

        val header = TextView(this).apply {
            text = "ADA Android Client"
            textSize = 18f
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val stateView = TextView(this).apply {
            text = "State: OFFLINE"
        }

        val backendUrlView = TextView(this).apply {
            text = "Backend: (not set)"
        }

        val transcriptText = TextView(this).apply {
            text = ""
            textSize = 18f
        }

        val transcriptScroll = ScrollView(this).apply {
            addView(transcriptText)
        }

        val jarvisView = JarvisLottieView(this)

        val requestOverlayBtn = Button(this).apply {
            text = "Grant Overlay Permission"
            setOnClickListener { requestOverlayPermissionIfNeeded() }
        }

        val requestMicBtn = Button(this).apply {
            text = "Grant Microphone Permission"
            setOnClickListener { requestMicPermissionIfNeeded() }
        }

        val startBtn = Button(this).apply {
            text = "Start Assistant Service"
            setOnClickListener {
                requestMicPermissionIfNeeded()
                requestOverlayPermissionIfNeeded()
                startAssistantService()
            }
        }

        val settingsBtn = Button(this).apply {
            text = "Settings (Backend URL)"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val visionBtn = Button(this).apply {
            text = "Open Vision (CameraX)"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CameraVisionActivity::class.java))
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop Assistant Service"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AssistantService::class.java))
            }
        }

        content.addView(header)
        content.addView(stateView)
        content.addView(backendUrlView)
        content.addView(jarvisView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (220 * resources.displayMetrics.density).toInt()
        ))
        content.addView(transcriptScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (280 * resources.displayMetrics.density).toInt()
        ))
        content.addView(requestOverlayBtn)
        content.addView(requestMicBtn)
        content.addView(startBtn)
        content.addView(settingsBtn)
        content.addView(visionBtn)
        content.addView(stopBtn)

        setContentView(root)

        lifecycleScope.launch {
            val url = withContext(Dispatchers.IO) {
                BackendConfig.appConfig(applicationContext).getBackendUrlOnce(BackendConfig.defaultUrl())
            }
            backendUrlView.text = "Backend: $url"
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    AssistantBus.uiState.collect { st ->
                        stateView.text = "State: ${st.name}"
                        jarvisView.setState(st.name)
                    }
                }
                launch {
                    AssistantBus.transcript.collect { line ->
                        transcriptText.append("${line.sender}: ${line.text}\n")
                        transcriptScroll.post { transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    private fun startAssistantService() {
        val i = Intent(this, AssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, i)
        } else {
            startService(i)
        }
    }

    private fun requestMicPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                audioPermissionRequestCode
            )
        }
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
