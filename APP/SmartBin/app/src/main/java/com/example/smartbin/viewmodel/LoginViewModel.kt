import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.smartbin.ApiClient
import com.example.smartbin.ApiResult
import com.example.smartbin.ApiResponse
import org.json.JSONObject

class LoginViewModel : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val apiClient: ApiClient = ApiClient.getInstance(
        "https://bromeo.pythonanywhere.com/",
        10000, // 10 seconds timeout
        Dispatchers.IO, // IO dispatcher
        true, // Enable logging
        3, // Retry count
        false // Trust all certificates
    )

    init {
        // Set initial login state
        _loginState.value = LoginState.Idle
    }

    fun login(binId: String, password: String) {
        _loginState.value = LoginState.Loading // Set state to Loading

        // Using viewModelScope for coroutines
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create request body
                val requestBody = createRequestBody(binId, password)

                // Perform login API call
                val result = apiClient.post("/login", requestBody)

                // Handle result using when, ensuring all states are covered
                when (result) {
                    is ApiResult.Success -> {
                        val response = result.data
                        if (response.statusCode == 200) {  // Replace with actual response success check
                            _loginState.postValue(LoginState.Success)
                        } else {
                            _loginState.postValue(LoginState.Error(response.message ?: "Unknown error"))
                        }
                    }
                    is ApiResult.Error -> {
                        _loginState.postValue(LoginState.Error(result.message ?: "Network error"))
                    }
                    else -> {
                        _loginState.postValue(LoginState.Error("Unexpected result"))
                    }
                }

            } catch (e: Exception) {
                _loginState.postValue(LoginState.Error("Unexpected error occurred: ${e.message}"))
                e.printStackTrace()
            }
        }
    }

    private fun createRequestBody(binId: String, password: String): String {
        val requestBody = HashMap<String, String>()
        requestBody["binId"] = binId
        requestBody["password"] = password
        return JSONObject(requestBody as Map<Any?, Any?>).toString()
    }
}

// Sealed class for different login states
sealed class LoginState {
    object Idle : LoginState()  // Initial state
    object Loading : LoginState()  // While loading
    object Success : LoginState()  // On successful login
    data class Error(val message: String) : LoginState()  // On error
}
