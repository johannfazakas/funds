package ro.jf.funds.fund.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.fund.api.FundApi
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import java.util.*

private val log = logger { }

class FundSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : FundApi {
    override suspend fun getFundById(userId: UUID, fundId: UUID): FundTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds/$fundId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            log.info { "Fund not found by id: $fundId" }
            return null
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get fund by id: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun getFundByName(userId: UUID, name: FundName): FundTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds/name/${name}") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            log.info { "Fund not found by name: $name" }
            return null
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get fund by name: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun listFunds(userId: UUID): ListTO<FundTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list funds: $response" }
            throw response.toApiException()
        }
        val funds = response.body<ListTO<FundTO>>()
        log.debug { "Retrieved funds: $funds" }
        return funds
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
