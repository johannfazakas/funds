package ro.jf.funds.client.web.api

import com.benasher44.uuid.uuidFrom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.sdk.FundClient
import ro.jf.funds.client.web.model.JsFund
import kotlin.js.JsExport
import kotlin.js.Promise

@JsExport
object FundApi {
    private val config = js("window.FUNDS_CONFIG")
    private val fundServiceUrl: String = config?.fundServiceUrl as? String ?: "http://localhost:5253"
    private val fundClient = FundClient(baseUrl = fundServiceUrl)

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

    fun createFund(userId: String, name: String): Promise<JsFund> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val fund = fundClient.createFund(uuid, name)
        JsFund(
            id = fund.id.toString(),
            name = fund.name.toString()
        )
    }

    fun deleteFund(userId: String, fundId: String): Promise<Unit> = GlobalScope.promise {
        fundClient.deleteFund(uuidFrom(userId), uuidFrom(fundId))
    }
}
