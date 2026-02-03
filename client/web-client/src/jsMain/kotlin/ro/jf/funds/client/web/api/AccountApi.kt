package ro.jf.funds.client.web.api

import com.benasher44.uuid.uuidFrom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.sdk.AccountClient
import ro.jf.funds.client.web.model.JsAccount
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.platform.api.model.FinancialUnit
import kotlin.js.JsExport
import kotlin.js.Promise

@JsExport
object AccountApi {
    private val config = js("window.FUNDS_CONFIG")
    private val fundServiceUrl: String = config?.fundServiceUrl as? String ?: "http://localhost:5253"
    private val accountClient = AccountClient(baseUrl = fundServiceUrl)

    fun listAccounts(userId: String): Promise<Array<JsAccount>> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val accounts = accountClient.listAccounts(uuid)
        accounts.map {
            JsAccount(
                id = it.id.toString(),
                name = it.name.toString(),
                unitType = it.unit.type.value,
                unitValue = it.unit.value,
            )
        }.toTypedArray()
    }

    fun createAccount(
        userId: String,
        name: String,
        unitType: String,
        unitValue: String,
    ): Promise<JsAccount> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val request = CreateAccountTO(
            name = AccountName(name),
            unit = FinancialUnit.of(unitType, unitValue),
        )
        val account = accountClient.createAccount(uuid, request)
        JsAccount(
            id = account.id.toString(),
            name = account.name.toString(),
            unitType = account.unit.type.value,
            unitValue = account.unit.value,
        )
    }

    fun deleteAccount(userId: String, accountId: String): Promise<Unit> = GlobalScope.promise {
        accountClient.deleteAccount(uuidFrom(userId), uuidFrom(accountId))
    }
}
