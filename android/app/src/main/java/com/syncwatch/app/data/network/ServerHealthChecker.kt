package com.syncwatch.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ServerHealthChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun checkHealth(serverHttpUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthUrl = "${serverHttpUrl.trimEnd('/')}/health"
            val request = Request.Builder().url(healthUrl).get().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}