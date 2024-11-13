package ro.jf.funds.fund.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.FundApi
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundTO
import java.util.*

private val log = logger { }

class FundSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient()
) : FundApi {
    override suspend fun listFunds(userId: UUID): ListTO<FundTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            throw response.toApiException()
        }
        val accounts = response.body<ListTO<FundTO>>()
        log.debug { "Retrieved accounts: $accounts" }
        return accounts
    }

    override suspend fun createFund(userId: UUID, request: CreateFundTO): FundTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.warn { "Unexpected response on create fund: $response" }
            throw response.toApiException()
        }
        return response.body()
    }
}
