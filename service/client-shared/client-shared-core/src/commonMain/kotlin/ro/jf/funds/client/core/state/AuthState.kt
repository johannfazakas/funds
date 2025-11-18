package ro.jf.funds.client.core.state

import com.benasher44.uuid.Uuid

data class AuthState(
    val userId: Uuid? = null,
    val username: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isAuthenticated: Boolean
        get() = userId != null
}
