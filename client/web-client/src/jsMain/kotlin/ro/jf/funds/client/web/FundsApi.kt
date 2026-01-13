@file:JsExport

package ro.jf.funds.client.web

import com.benasher44.uuid.uuidFrom
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.sdk.AuthenticationClient
import ro.jf.funds.client.sdk.FundClient
import kotlin.js.Promise

@JsExport
object FundsApi {
    private val config = js("window.FUNDS_CONFIG")
    private val userServiceUrl: String = config?.userServiceUrl as? String ?: "http://localhost:5247"
    private val fundServiceUrl: String = config?.fundServiceUrl as? String ?: "http://localhost:5253"

    private val authenticationClient = AuthenticationClient(baseUrl = userServiceUrl)
    private val fundClient = FundClient(baseUrl = fundServiceUrl)

    fun loginWithUsername(username: String): Promise<JsUser?> = GlobalScope.promise {
        val user = authenticationClient.loginWithUsername(username)
        user?.let {
            JsUser(
                id = it.id.toString(),
                username = it.username
            )
        }
    }

    fun listFunds(userId: String): Promise<Array<JsFund>> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val funds = fundClient.listFunds(uuid)
        funds.map {
            JsFund(
                id = it.id.toString(),
                name = it.name.toString()
            )
        }.toTypedArray()
    }
}

@JsExport
data class JsUser(
    val id: String,
    val username: String
)

@JsExport
data class JsFund(
    val id: String,
    val name: String
)

fun main() {
    val ro = js("{}")
    ro.jf = js("{}")
    ro.jf.funds = js("{}")
    ro.jf.funds.client = js("{}")
    ro.jf.funds.client.web = js("{}")
    ro.jf.funds.client.web.FundsApi = FundsApi
    window.asDynamic().ro = ro
}
