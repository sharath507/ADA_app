package com.example.ada.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AIClient(
    private val baseUrl: String,
) {
    private val http = OkHttpClient()

    suspend fun startSession(): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/start")
            .post(ByteArray(0).toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Backend error: HTTP ${resp.code}")
            }
        }
    }

    suspend fun sendVoiceChunk(pcm16le: ByteArray): Unit = withContext(Dispatchers.IO) {
        val body = pcm16le.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("$baseUrl/voice")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Backend error: HTTP ${resp.code}")
            }
        }
    }

    suspend fun sendText(text: String): String = withContext(Dispatchers.IO) {
        val json = "{\"text\":${text.toJsonString()}}"
        val body = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$baseUrl/text")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Backend error: HTTP ${resp.code}")
            }
            resp.body?.string().orEmpty()
        }
    }

    private fun String.toJsonString(): String {
        val escaped = buildString(length + 16) {
            for (ch in this@toJsonString) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
        return "\"$escaped\""
    }
}
