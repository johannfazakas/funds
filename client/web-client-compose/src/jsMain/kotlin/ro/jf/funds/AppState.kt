package ro.jf.funds

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import kotlinx.browser.localStorage
import ro.jf.funds.client.sdk.AuthenticationClient
import ro.jf.funds.client.sdk.FundClient

private const val USER_ID_KEY = "funds_user_id"
private const val DEFAULT_USER_SERVICE_URL = "http://localhost:5247"
private const val DEFAULT_FUND_SERVICE_URL = "http://localhost:5253"

object AppState {
    val authenticationClient = AuthenticationClient(baseUrl = DEFAULT_USER_SERVICE_URL)
    val fundClient = FundClient(baseUrl = DEFAULT_FUND_SERVICE_URL)

    fun getUserId(): Uuid? {
        return localStorage.getItem(USER_ID_KEY)?.let { uuidFrom(it) }
    }

    fun setUserId(userId: Uuid) {
        localStorage.setItem(USER_ID_KEY, userId.toString())
    }

    fun clearUserId() {
        localStorage.removeItem(USER_ID_KEY)
    }

    fun isLoggedIn(): Boolean = getUserId() != null
}
