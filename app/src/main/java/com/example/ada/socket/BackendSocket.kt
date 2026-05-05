package com.example.ada.socket

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class BackendSocket(
    baseUrl: String,
) {

    private val socket: Socket = IO.socket(baseUrl)

    fun connect(
        onConnected: (() -> Unit)? = null,
        onDisconnected: (() -> Unit)? = null,
        onAudioData: ((ByteArray) -> Unit)? = null,
        onTranscription: ((sender: String, text: String) -> Unit)? = null,
    ) {
        socket.on(Socket.EVENT_CONNECT) {
            onConnected?.invoke()
        }
        socket.on(Socket.EVENT_DISCONNECT) {
            onDisconnected?.invoke()
        }

        socket.on("audio_data") { args ->
            // backend emits: {'data': list(data_bytes)}
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            val arr = obj.optJSONArray("data") ?: return@on
            val bytes = ByteArray(arr.length())
            for (i in 0 until arr.length()) {
                bytes[i] = (arr.optInt(i) and 0xFF).toByte()
            }
            onAudioData?.invoke(bytes)
        }

        socket.on("transcription") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            val sender = obj.optString("sender")
            val text = obj.optString("text")
            if (sender.isNotBlank() && text.isNotBlank()) {
                onTranscription?.invoke(sender, text)
            }
        }

        socket.connect()
    }

    fun disconnect() {
        socket.disconnect()
        socket.off()
    }
}
