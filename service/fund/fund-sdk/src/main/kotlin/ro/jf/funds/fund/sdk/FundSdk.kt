package ro.jf.funds.fund.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.client.appendPageRequest
import ro.jf.funds.platform.jvm.client.appendSortRequest
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException
import ro.jf.funds.fund.api.FundApi
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundSortField
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.api.model.UpdateFundTO

private val log = logger { }

class FundSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : FundApi {
    override suspend fun getFundById(userId: Uuid, fundId: Uuid): FundTO? = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds/$fundId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            log.info { "Fund not found by id: $fundId" }
            return@withSuspendingSpan null
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get fund by id: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun getFundByName(userId: Uuid, name: FundName): FundTO? = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds/name/${name}".encodeURLPath()) {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            log.info { "Fund not found by name: $name" }
            return@withSuspendingSpan null
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get fund by name: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun listFunds(
        userId: Uuid,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<FundSortField>?,
    ): PageTO<FundTO> = withSuspendingSpan {
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
            log.warn { "Unexpected response on list funds: $response" }
            throw response.toApiException()
        }
        val funds = response.body<PageTO<FundTO>>()
        log.debug { "Retrieved funds: $funds" }
        funds
    }

    override suspend fun createFund(userId: Uuid, request: CreateFundTO): FundTO = withSuspendingSpan {
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
        response.body()
    }
    override suspend fun updateFund(userId: Uuid, fundId: Uuid, request: UpdateFundTO): FundTO = withSuspendingSpan {
        val response = httpClient.patch("$baseUrl$BASE_PATH/funds/$fundId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on update fund: $response" }
            throw response.toApiException()
        }
        response.body()
    }

}
