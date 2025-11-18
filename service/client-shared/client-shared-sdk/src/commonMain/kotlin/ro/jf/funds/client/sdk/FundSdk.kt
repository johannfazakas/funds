package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.client.api.model.FundTO
import ro.jf.funds.client.api.model.ListTO

private const val LOCALHOST_BASE_URL = "http://localhost:5253"
private const val BASE_PATH = "/funds-api/fund/v1"

class FundSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("FundSdk")

    suspend fun listFunds(userId: Uuid): ListTO<FundTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list funds: $response" }
            throw Exception("Failed to list funds: ${response.status}")
        }
        val funds = response.body<ListTO<FundTO>>()
        log.d { "Retrieved funds: $funds" }
        return funds
    }
}
