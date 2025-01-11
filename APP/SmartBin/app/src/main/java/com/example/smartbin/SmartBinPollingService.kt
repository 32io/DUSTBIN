package com.example.smartbin

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class SmartBinPollingService(private val context: Context) {
    private val pollingJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + pollingJob)
    private val isPolling = AtomicBoolean(false)
    private val _connectionState = MutableLiveData<ConnectionState>()
    private val _notifications = MutableLiveData<List<Notification>>()

    val connectionState: LiveData<ConnectionState> = _connectionState
    val notifications: LiveData<List<Notification>> = _notifications

    private val POLLING_INTERVAL = 5000L // 5 seconds
    private val BASE_URL = "https://bromeo.pythonanywhere.com/notifications"

    // Define the Notification data class within the service
    data class Notification(
        val message: String,
        val dustbinId: String?,
        val state: String
    )

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, ERROR
    }

    fun startPolling() {
        if (isPolling.getAndSet(true)) {
            return // Already polling
        }

        coroutineScope.launch {
            _connectionState.postValue(ConnectionState.CONNECTED)

            while (isPolling.get()) {
                try {
                    fetchNotifications()
                    delay(POLLING_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}", e)
                    _connectionState.postValue(ConnectionState.ERROR)

                    // Implement exponential backoff
                    delay(calculateBackoffDelay())

                    // Try to reconnect
                    _connectionState.postValue(ConnectionState.CONNECTED)
                }
            }
        }
    }

    fun stopPolling() {
        isPolling.set(false)
        pollingJob.cancel()
        _connectionState.postValue(ConnectionState.DISCONNECTED)
    }

    private var retryAttempt = 0
    private fun calculateBackoffDelay(): Long {
        val delay = minOf(POLLING_INTERVAL * (1 shl retryAttempt), 60000L) // Max 1 minute
        retryAttempt = minOf(retryAttempt + 1, 6) // Max 6 retries
        return delay
    }

    private suspend fun fetchNotifications() {
        withContext(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("SmartBinPrefs", Context.MODE_PRIVATE)
            val sessionCookie = sharedPrefs.getString("session_cookie", null)

            val url = URL(BASE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", sessionCookie)
                connectTimeout = 10000
                readTimeout = 10000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()

                    processNotifications(response)
                    retryAttempt = 0 // Reset retry counter on success
                } else {
                    throw Exception("Server returned code: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun processNotifications(response: String) {
        try {
            val jsonResponse = JSONObject(response)
            val notificationsArray = jsonResponse.getJSONArray("notifications")
            val notificationsList = mutableListOf<Notification>()

            for (i in 0 until notificationsArray.length()) {
                val notificationObj = notificationsArray.getJSONObject(i)
                val message = notificationObj.getString("message")
                val dustbinId = notificationObj.optString("dustbin_id", null)
                val state = notificationObj.optString("state", "")

                notificationsList.add(
                    Notification(message, dustbinId, state)
                )
            }

            _notifications.postValue(notificationsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notifications: ${e.message}", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "SmartBinPollingService"
    }
}