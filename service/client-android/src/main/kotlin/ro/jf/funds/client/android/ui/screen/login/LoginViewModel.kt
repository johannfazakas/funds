package ro.jf.funds.client.android.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.jf.funds.client.core.repository.AuthRepository
import ro.jf.funds.client.core.state.AuthState

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val log = Logger.withTag("LoginViewModel")

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(username: String) {
        if (username.isBlank()) {
            _authState.value = _authState.value.copy(error = "Username cannot be empty")
            return
        }

        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            try {
                val user = authRepository.loginWithUsername(username.trim())
                if (user != null) {
                    _authState.value = AuthState(
                        userId = user.id,
                        username = user.username,
                        isLoading = false
                    )
                    log.i { "User logged in: ${user.username}" }
                } else {
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = "User not found"
                    )
                }
            } catch (e: Exception) {
                log.e(e) { "Login failed" }
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = "Login failed: ${e.message}"
                )
            }
        }
    }
}
