package com.example.ada.state

import kotlinx.coroutines.flow.MutableStateFlow

object VisionBus {
    val isStreaming = MutableStateFlow(false)
    val lastUploadTsMs = MutableStateFlow(0L)
    val lastError = MutableStateFlow<String?>(null)
}
