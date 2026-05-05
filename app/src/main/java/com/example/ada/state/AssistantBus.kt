package com.example.ada.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object AssistantBus {

    enum class UiState {
        OFFLINE,
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        ERROR,
    }

    data class TranscriptLine(
        val sender: String,
        val text: String,
        val tsMs: Long = System.currentTimeMillis(),
    )

    val uiState = MutableStateFlow(UiState.OFFLINE)
    val transcript = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 64)
}
