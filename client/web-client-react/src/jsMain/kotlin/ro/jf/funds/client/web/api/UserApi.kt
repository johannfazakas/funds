package ro.jf.funds.client.web.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.sdk.AuthenticationClient
import ro.jf.funds.client.web.model.JsUser
import kotlin.js.JsExport
import kotlin.js.Promise

@JsExport
object UserApi {
    private val config = js("window.FUNDS_CONFIG")
    private val userServiceUrl: String = config?.userServiceUrl as? String ?: "http://localhost:5247"
    private val authenticationClient = AuthenticationClient(baseUrl = userServiceUrl)

    fun loginWithUsername(username: String): Promise<JsUser?> = GlobalScope.promise {
        val user = authenticationClient.loginWithUsername(username)
        user?.let {
            JsUser(
                id = it.id.toString(),
                username = it.username
            )
        }
    }
}
