package ro.jf.bk.investment.sdk

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ro.jf.bk.investment.api.model.InvestmentPosition

private const val LOCALHOST_BASE_URL = "http://localhost:5275"

class InvestmentSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL
) {
    private val httpClient = OkHttpClient()

    fun listPositions(symbol: String): List<InvestmentPosition> {
        val response: List<InvestmentPosition> = httpClient.newCall(
            Request.Builder()
                .url("$baseUrl/api/investment/instruments/$symbol/positions")
                .get()
                .build()
        )
            .execute()
            .let { it.body?.string()?.let(Json.Default::decodeFromString) ?: error("Empty response body") }
        return response
    }
}
