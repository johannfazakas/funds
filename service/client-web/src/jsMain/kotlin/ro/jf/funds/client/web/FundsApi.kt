@file:JsExport

package ro.jf.funds.client.web

import com.benasher44.uuid.uuidFrom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.core.repository.AuthRepository
import ro.jf.funds.client.core.repository.FundRepository
import kotlin.js.Promise

@JsExport
object FundsApi {
    private val authRepository = AuthRepository()
    private val fundRepository = FundRepository()

    fun loginWithUsername(username: String): Promise<JsUser?> = GlobalScope.promise {
        val user = authRepository.loginWithUsername(username)
        user?.let {
            JsUser(
                id = it.id.toString(),
                username = it.username
            )
        }
    }

    fun listFunds(userId: String): Promise<Array<JsFund>> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val funds = fundRepository.listFunds(uuid)
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
