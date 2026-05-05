package com.example.ada.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ada.config.BackendConfig
import com.example.ada.state.VisionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraVisionActivity : AppCompatActivity() {

    private val cameraPermissionRequestCode = 2001

    private lateinit var previewView: PreviewView
    private lateinit var toggleBtn: Button

    private var isStreaming = false

    private var cameraExecutor: ExecutorService? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var uploader: VisionUploader? = null

    private var lastUploadMs = 0L
    private val uploadEveryMs = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)

        toggleBtn = Button(this).apply {
            text = "Start Vision Streaming"
            setOnClickListener {
                isStreaming = !isStreaming
                text = if (isStreaming) "Stop Vision Streaming" else "Start Vision Streaming"
                VisionBus.isStreaming.value = isStreaming
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toggleBtn)
            addView(previewView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }

        setContentView(root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        scope.launch {
            val url = withContext(Dispatchers.IO) {
                BackendConfig.appConfig(applicationContext).getBackendUrlOnce(BackendConfig.defaultUrl())
            }
            uploader = VisionUploader(url)
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        }
    }

    override fun onDestroy() {
        VisionBus.isStreaming.value = false
        cameraExecutor?.shutdown()
        cameraExecutor = null
        super.onDestroy()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor!!, ImageAnalysis.Analyzer { imageProxy ->
                onFrame(imageProxy)
            })

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFrame(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (!isStreaming || (now - lastUploadMs) < uploadEveryMs) {
                return
            }

            val jpeg = imageProxyToJpeg(image) ?: return
            lastUploadMs = now

            scope.launch {
                try {
                    uploader?.uploadJpeg(jpeg)
                    VisionBus.lastUploadTsMs.value = System.currentTimeMillis()
                    VisionBus.lastError.value = null
                } catch (_: Exception) {
                    VisionBus.lastError.value = "upload_failed"
                }
            }
        } finally {
            image.close()
        }
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray? {
        return when (image.format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes.firstOrNull()?.buffer ?: return null
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes
            }

            ImageFormat.YUV_420_888 -> {
                yuv420888ToJpeg(image, quality = 70)
            }

            else -> null
        }
    }

    private fun yuv420888ToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        val nv21 = yuv420888ToNv21(image) ?: return null

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        val ok = yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        if (!ok) return null
        return out.toByteArray()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray? {
        val yPlane = image.planes.getOrNull(0) ?: return null
        val uPlane = image.planes.getOrNull(1) ?: return null
        val vPlane = image.planes.getOrNull(2) ?: return null

        val width = image.width
        val height = image.height

        val ySize = width * height
        val uvSize = width * height / 2
        val out = ByteArray(ySize + uvSize)

        // Copy Y
        copyPlane(
            plane = yPlane,
            width = width,
            height = height,
            out = out,
            outOffset = 0,
            outPixelStride = 1
        )

        // NV21 expects VU interleaving
        // The U/V plane order in ImageProxy for YUV_420_888 is typically: Y, U, V
        // but we explicitly write V then U to match NV21.
        val chromaWidth = width / 2
        val chromaHeight = height / 2

        var outIndex = ySize
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until chromaHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uPixelStride
                val vIndex = vRowStart + col * vPixelStride

                out[outIndex++] = vBuf.get(vIndex)
                out[outIndex++] = uBuf.get(uIndex)
            }
        }

        return out
    }

    private fun copyPlane(
        plane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int,
        outPixelStride: Int,
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        var outIndex = outOffset
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                out[outIndex] = buffer.get(rowStart + col * pixelStride)
                outIndex += outPixelStride
            }
        }
    }
}
