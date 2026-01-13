package ro.jf.funds.client.sdk

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
