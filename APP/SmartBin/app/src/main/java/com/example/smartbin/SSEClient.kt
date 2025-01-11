package com.example.smartbin

import android.util.Log
import okhttp3.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SSEClient(
    private val url: String,
    private val listener: SSEListener,
    private val sessionCookie: String? = null
) {
    private var call: Call? = null
    private var job: Job? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // Infinite timeout for SSE
        .build()

    interface SSEListener {
        fun onMessage(message: JSONObject)
        fun onError(error: String)
        fun onConnectionEstablished()
    }

    fun connect() {
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        // Add session cookie if provided
                        sessionCookie?.let {
                            addHeader("Cookie", it)
                        }
                    }
                    .build()

                call = client.newCall(request)
                call?.execute()?.use { response ->
                    if (!response.isSuccessful) {
                        listener.onError("Failed to connect: ${response.code}")
                        return@launch
                    }

                    listener.onConnectionEstablished()

                    response.body?.let { responseBody ->
                        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                        var line: String?
                        val buffer = StringBuilder()

                        while (isActive) {
                            line = reader.readLine()

                            if (line == null) {
                                delay(1000)  // Prevent busy waiting
                                continue
                            }

                            when {
                                line.startsWith("data: ") -> {
                                    buffer.append(line.removePrefix("data: "))
                                }
                                line.isEmpty() -> {
                                    // Empty line indicates end of event
                                    if (buffer.isNotEmpty()) {
                                        try {
                                            val message = JSONObject(buffer.toString())
                                            withContext(Dispatchers.Main) {
                                                listener.onMessage(message)
                                            }
                                            buffer.clear()
                                        } catch (e: Exception) {
                                            Log.e("SSEClient", "Error parsing JSON: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onError("Connection error: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        call?.cancel()
    }

    companion object {
        private const val TAG = "SSEClient"
    }
}