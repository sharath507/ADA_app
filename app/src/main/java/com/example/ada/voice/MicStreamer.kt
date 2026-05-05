package com.example.ada.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MicStreamer(
    private val scope: CoroutineScope,
    private val onPcmChunk: suspend (ByteArray) -> Unit,
) {

    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (job != null) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBuf.coerceAtLeast(sampleRate / 2))

        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        recorder = r

        job = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val buf = ByteArray(2048)
            r.startRecording()

            while (isActive) {
                val read = r.read(buf, 0, buf.size)
                if (read > 0) {
                    val chunk = buf.copyOf(read)
                    onPcmChunk(chunk)
                }
            }

            try {
                r.stop()
            } catch (_: Exception) {
            }
            r.release()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder = null
    }
}
