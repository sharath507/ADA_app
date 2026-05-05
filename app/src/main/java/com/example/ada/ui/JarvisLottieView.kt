package com.example.ada.ui

import android.content.Context
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class JarvisLottieView(context: Context) : LottieAnimationView(context) {

    private var currentAsset: String? = null

    init {
        repeatCount = LottieDrawable.INFINITE
        setState("OFFLINE")
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        speed = 1.0f
    }

    fun setState(state: String) {
        if (state.isBlank()) return

        val (asset, spd) = when (state.uppercase()) {
            "LISTENING" -> "jarvis_listening.json" to 1.0f
            "THINKING" -> "jarvis_thinking.json" to 1.0f
            "SPEAKING" -> "jarvis_speaking.json" to 1.0f
            "CONNECTING" -> "jarvis_thinking.json" to 1.0f
            "ERROR" -> "jarvis_offline.json" to 1.0f
            "OFFLINE" -> "jarvis_offline.json" to 1.0f
            else -> "jarvis_listening.json" to 1.0f
        }

        speed = spd

        if (currentAsset != asset) {
            currentAsset = asset
            setAnimation(asset)
            progress = 0f
            playAnimation()
        } else if (!isAnimating) {
            playAnimation()
        }
    }
}
