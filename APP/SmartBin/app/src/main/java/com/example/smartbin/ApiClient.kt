package com.example.smartbin

import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int? = null, val message: String) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

data class ApiResponse(
    val success: Boolean,
    val data: JSONObject? = null,
    val message: String? = null,
    val statusCode: Int
)

class ApiClient private constructor(
    private val baseUrl: String,
    private val timeout: Int = Duration.parse("10s").inWholeMilliseconds.toInt(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableLogging: Boolean = false,
    private val retryCount: Int = 3,
    private val trustAllCertificates: Boolean = false
) {
    companion object {
        private const val TAG = "ApiClient"
        private const val BASE_URL = "https://bromeo.pythonanywhere.com/"
        private var instance: ApiClient? = null
        private val defaultHeaders = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        @JvmStatic
        fun getInstance(
            baseUrl: String = BASE_URL,
            timeout: Int = Duration.parse("10s").inWholeMilliseconds.toInt(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            enableLogging: Boolean = false,
            retryCount: Int = 3,
            trustAllCertificates: Boolean = false
        ): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient(
                    baseUrl,
                    timeout,
                    dispatcher,
                    enableLogging,
                    retryCount,
                    trustAllCertificates
                ).also { instance = it }
            }
        }

        // Signup method
        @JvmStatic
        fun signup(email: String, password: String): ApiResult<ApiResponse> {
            val requestBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            return runBlocking {
                getInstance().post("/signup", requestBody)
            }
        }

        // Login method
        @JvmStatic
        fun login(email: String, password: String): ApiResult<ApiResponse> {
            val requestBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            return runBlocking {
                getInstance().post("/login", requestBody)
            }
        }

        // Add Dustbin method
        @JvmStatic
        fun addDustbin(dustbinId: String, location: String? = null): ApiResult<ApiResponse> {
            val requestBody = JSONObject().apply {
                put("dustbin_id", dustbinId)
                location?.let { put("location", it) }
            }.toString()

            return runBlocking {
                getInstance().post("/add_dustbin", requestBody)
            }
        }

        // Initiate Payment method
        @JvmStatic
        fun initiatePayment(
            email: String,
            dustbinId: String,
            phoneNumber: String
        ): ApiResult<ApiResponse> {
            val requestBody = JSONObject().apply {
                put("email", email)
                put("dustbin_id", dustbinId)
                put("phone", phoneNumber)
            }.toString()

            return runBlocking {
                getInstance().post("/payment_start", requestBody)
            }
        }

        // Update Dustbin State method
        @JvmStatic
        fun updateDustbinState(dustbinId: String, state: String): ApiResult<ApiResponse> {
            val requestBody = JSONObject().apply {
                put("dustbin_id", dustbinId)
                put("state", state)
            }.toString()

            return runBlocking {
                getInstance().post("/dustbin_state", requestBody)
            }
        }

        // Get Dustbin Data method
        @JvmStatic
        fun getDustbinData(dustbinId: String): ApiResult<ApiResponse> = runBlocking {
            getInstance().get("/dustbin_data?dustbin_id=$dustbinId")
        }

        // Get All Dustbins method
        @JvmStatic
        fun getAllDustbins(): ApiResult<ApiResponse> = runBlocking {
            getInstance().get("/all_dustbins")
        }
    }

    // HTTP POST method
    suspend fun post(
        endpoint: String,
        requestBody: String? = null,
        headers: Map<String, String>? = null
    ): ApiResult<ApiResponse> {
        return makeRequest("POST", endpoint, requestBody?.let { mapOf("body" to it) }, headers)
    }

    // HTTP GET method
    suspend fun get(
        endpoint: String,
        headers: Map<String, String>? = null
    ): ApiResult<ApiResponse> {
        return makeRequest("GET", endpoint, null, headers)
    }

    private suspend fun makeRequest(
        method: String,
        endpoint: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null
    ): ApiResult<ApiResponse> = withContext(dispatcher) {
        val url = "$baseUrl$endpoint"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeout
            readTimeout = timeout
            headers?.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        if (trustAllCertificates && connection is HttpsURLConnection) setupTrustAllCertificates(
            connection
        )

        try {
            connection.connect()
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            ApiResult.Success(
                ApiResponse(
                    success = responseCode in 200..299,
                    data = JSONObject(response),
                    statusCode = responseCode
                )
            )
        } catch (e: IOException) {
            ApiResult.Error(message = e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    private fun setupTrustAllCertificates(connection: HttpsURLConnection) {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        connection.sslSocketFactory = sslContext.socketFactory

        connection.hostnameVerifier = HostnameVerifier { _, _ -> true } // Corrected assignment
    }
}
