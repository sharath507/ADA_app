package com.example.ada.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class VisionUploader(
    private val baseUrl: String,
) {

    private val http = OkHttpClient()

    suspend fun uploadJpeg(jpegBytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val body = jpegBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("$baseUrl/vision")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Backend error: HTTP ${resp.code}")
            }
        }
    }
}
