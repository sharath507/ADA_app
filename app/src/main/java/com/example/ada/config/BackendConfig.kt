package com.example.ada.config

import android.content.Context

object BackendConfig {
    private const val DEFAULT_URL = "http://10.0.2.2:8000"

    fun defaultUrl(): String = DEFAULT_URL

    fun appConfig(context: Context): AppConfig = AppConfig(context.applicationContext)
}
