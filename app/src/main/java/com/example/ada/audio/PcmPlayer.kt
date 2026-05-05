package com.example.ada.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class PcmPlayer(
    private val sampleRate: Int = 24000,
) {

    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return

        val candidates = listOf(sampleRate, 24000, 16000).distinct()
        for (sr in candidates) {
            val minBuf = AudioTrack.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuf <= 0) {
                Log.w("PcmPlayer", "getMinBufferSize failed for sampleRate=$sr (minBuf=$minBuf)")
                continue
            }

            try {
                val t = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBuf,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                if (t.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w("PcmPlayer", "AudioTrack not initialized for sampleRate=$sr (state=${t.state})")
                    t.release()
                    continue
                }

                t.play()
                track = t
                Log.i("PcmPlayer", "AudioTrack started sampleRate=$sr buffer=$minBuf")
                return
            } catch (e: Exception) {
                Log.e("PcmPlayer", "Failed to start AudioTrack for sampleRate=$sr", e)
            }
        }

        Log.e("PcmPlayer", "No supported audio output configuration found; audio playback disabled")
    }

    fun stop() {
        val t = track ?: return
        try {
            t.pause()
            t.flush()
            t.stop()
        } catch (_: Exception) {
        }
        t.release()
        track = null
    }

    fun writePcm(pcm16le: ByteArray) {
        val t = track ?: return
        try {
            t.write(pcm16le, 0, pcm16le.size)
        } catch (_: Exception) {
        }
    }
}
