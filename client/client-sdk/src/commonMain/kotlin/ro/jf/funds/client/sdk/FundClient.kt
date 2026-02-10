package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundSortField
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest

private const val LOCALHOST_BASE_URL = "http://localhost:5253"
private const val BASE_PATH = "/funds-api/fund/v1"

class FundClient(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("FundClient")

    suspend fun listFunds(
        userId: Uuid,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<FundSortField>? = null,
    ): PageTO<FundTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            url {
                parameters.appendPageRequest(pageRequest)
                parameters.appendSortRequest(sortRequest)
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list funds: $response" }
            throw Exception("Failed to list funds: ${response.status}")
        }
        val funds = response.body<PageTO<FundTO>>()
        log.d { "Retrieved funds: $funds" }
        return funds
    }

    suspend fun createFund(userId: Uuid, name: String): FundTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(CreateFundTO(FundName(name)))
        }
        if (response.status != HttpStatusCode.Created) {
            log.w { "Unexpected response on create fund: $response" }
            throw Exception("Failed to create fund: ${response.status}")
        }
        return response.body()
    }

    suspend fun deleteFund(userId: Uuid, fundId: Uuid) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/funds/$fundId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.w { "Unexpected response on delete fund: $response" }
            throw Exception("Failed to delete fund: ${response.status}")
        }
    }
}
