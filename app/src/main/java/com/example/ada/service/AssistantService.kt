package com.example.ada.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.ada.MainActivity
import com.example.ada.R
import com.example.ada.ai.AIClient
import com.example.ada.audio.PcmPlayer
import com.example.ada.config.BackendConfig
import com.example.ada.socket.BackendSocket
import com.example.ada.state.AssistantBus
import com.example.ada.ui.overlay.OverlayUi
import com.example.ada.voice.MicStreamer
import com.example.ada.voice.VoiceManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AssistantService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayUi: OverlayUi? = null
    private var voiceManager: VoiceManager? = null
    private var aiClient: AIClient? = null
    private var micStreamer: MicStreamer? = null
    private var backendSocket: BackendSocket? = null
    private var pcmPlayer: PcmPlayer? = null

    override fun onCreate() {
        super.onCreate()

        startForeground(NOTIFICATION_ID, buildNotification())

        overlayUi = OverlayUi(applicationContext)
        overlayUi?.show()
        overlayUi?.setState(OverlayUi.State.LISTENING)
        AssistantBus.uiState.value = AssistantBus.UiState.CONNECTING

        voiceManager = VoiceManager(applicationContext)
        try {
            pcmPlayer = PcmPlayer(sampleRate = 24000)
            pcmPlayer?.start()
        } catch (e: Exception) {
            Log.e("AssistantService", "Failed to start PcmPlayer", e)
        }

        serviceScope.launch {
            overlayUi?.setState(OverlayUi.State.THINKING)
            AssistantBus.uiState.value = AssistantBus.UiState.CONNECTING

            val backendUrl = try {
                BackendConfig.appConfig(applicationContext)
                    .getBackendUrlOnce(BackendConfig.defaultUrl())
            } catch (e: Exception) {
                Log.e("AssistantService", "Failed to read backend url", e)
                BackendConfig.defaultUrl()
            }

            aiClient = AIClient(baseUrl = backendUrl)

            try {
                backendSocket = BackendSocket(baseUrl = backendUrl)
                backendSocket?.connect(
                    onConnected = {
                        overlayUi?.setState(OverlayUi.State.LISTENING)
                        AssistantBus.uiState.value = AssistantBus.UiState.LISTENING
                    },
                    onDisconnected = {
                        overlayUi?.setState(OverlayUi.State.LISTENING)
                        AssistantBus.uiState.value = AssistantBus.UiState.OFFLINE
                    },
                    onAudioData = { bytes ->
                        overlayUi?.setState(OverlayUi.State.SPEAKING)
                        AssistantBus.uiState.value = AssistantBus.UiState.SPEAKING
                        pcmPlayer?.writePcm(bytes)
                    },
                    onTranscription = { sender, text ->
                        AssistantBus.transcript.tryEmit(AssistantBus.TranscriptLine(sender = sender, text = text))
                        if (sender.equals("User", ignoreCase = true)) {
                            overlayUi?.setState(OverlayUi.State.LISTENING)
                            AssistantBus.uiState.value = AssistantBus.UiState.LISTENING
                        } else {
                            overlayUi?.setState(OverlayUi.State.SPEAKING)
                            AssistantBus.uiState.value = AssistantBus.UiState.SPEAKING
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("AssistantService", "Failed to connect BackendSocket", e)
            }

            try {
                aiClient?.startSession()
            } catch (_: Exception) {
                voiceManager?.speak("Backend not reachable yet")
                AssistantBus.uiState.value = AssistantBus.UiState.ERROR
            }

            try {
                micStreamer = MicStreamer(
                    scope = serviceScope,
                    onPcmChunk = { bytes ->
                        try {
                            aiClient?.sendVoiceChunk(bytes)
                        } catch (_: Exception) {
                        }
                    }
                )

                micStreamer?.start()
                overlayUi?.setState(OverlayUi.State.LISTENING)
                AssistantBus.uiState.value = AssistantBus.UiState.LISTENING
            } catch (e: Exception) {
                Log.e("AssistantService", "Failed to start MicStreamer", e)
            }
        }

        voiceManager?.speak("ADA is online")
    }

    override fun onDestroy() {
        micStreamer?.stop()
        micStreamer = null

        backendSocket?.disconnect()
        backendSocket = null

        pcmPlayer?.stop()
        pcmPlayer = null

        overlayUi?.hide()
        overlayUi = null
        voiceManager?.shutdown()
        voiceManager = null
        aiClient = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureChannel()

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ADA Assistant")
            .setContentText("Running")
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADA Assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ada_assistant"
        private const val NOTIFICATION_ID = 100
    }
}
