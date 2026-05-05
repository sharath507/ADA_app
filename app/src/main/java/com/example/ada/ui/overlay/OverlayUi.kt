package com.example.ada.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayUi(private val context: Context) {

    enum class State {
        LISTENING,
        THINKING,
        SPEAKING
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }

        if (view != null) return

        val tv = TextView(context).apply {
            text = "ADA"
            textSize = 22f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        wm.addView(tv, params)
        view = tv
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }

        val v = view ?: return
        wm.removeView(v)
        view = null
    }

    fun setState(state: State) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setState(state) }
            return
        }

        val tv = view ?: return
        tv.text = when (state) {
            State.LISTENING -> "ADA: Listening"
            State.THINKING -> "ADA: Thinking"
            State.SPEAKING -> "ADA: Speaking"
        }
    }
}
