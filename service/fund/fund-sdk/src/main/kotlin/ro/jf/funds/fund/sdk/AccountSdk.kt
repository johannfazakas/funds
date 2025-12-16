package ro.jf.funds.fund.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.api.model.ListTO
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException
import ro.jf.funds.fund.api.AccountApi
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO

private val log = logger { }

class AccountSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : AccountApi {
    override suspend fun listAccounts(userId: Uuid): ListTO<AccountTO> = withSuspendingSpan {
        val response = httpClient.get("$baseUrl/funds-api/fund/v1/accounts") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            throw response.toApiException()
        }
        val accounts = response.body<ListTO<AccountTO>>()
        log.debug { "Retrieved accounts: $accounts" }
        accounts
    }

    override suspend fun findAccountById(userId: Uuid, accountId: Uuid): AccountTO? = withSuspendingSpan {
        val response = httpClient.get("$baseUrl/funds-api/fund/v1/accounts/$accountId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                log.debug { "Retrieved account: $response" }
                response.body()
            }

            HttpStatusCode.NotFound -> {
                log.info { "Account $accountId not found by id, response: $response" }
                null
            }

            else -> {
                log.warn { "Error response on find account by id: $response" }
                throw response.toApiException()
            }
        }
    }

    override suspend fun createAccount(userId: Uuid, request: CreateAccountTO): AccountTO = withSuspendingSpan {
        val response = httpClient.post("$baseUrl/funds-api/fund/v1/accounts") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        when (response.status) {
            HttpStatusCode.Created -> response.body()
            else -> {
                log.warn { "Unexpected response on create account: $response" }
                throw response.toApiException()
            }
        }
    }

    override suspend fun deleteAccountById(userId: Uuid, accountId: Uuid) = withSuspendingSpan {
        val response = httpClient.delete("$baseUrl/funds-api/fund/v1/accounts/$accountId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.warn { "Unexpected response on delete account: $response" }
            throw response.toApiException()
        }
    }
}