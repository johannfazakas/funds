package ro.jf.funds.account.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.AccountApi
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.sdk.client.createHttpClient
import ro.jf.funds.commons.sdk.client.toApiException
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5211"
private const val BASE_PATH = "/bk-api/account/v1"

private val log = logger { }

class AccountSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : AccountApi {
    override suspend fun listAccounts(userId: UUID): ListTO<AccountTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/accounts") {
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
        return accounts
    }

    override suspend fun findAccountById(userId: UUID, accountId: UUID): AccountTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/accounts/$accountId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        return when (response.status) {
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

    override suspend fun createAccount(userId: UUID, request: CreateAccountTO): AccountTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/accounts/currency") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return when (response.status) {
            HttpStatusCode.Created -> response.body()
            else -> {
                log.warn { "Unexpected response on create currency account: $response" }
                throw throw response.toApiException()
            }
        }
    }

    override suspend fun deleteAccountById(userId: UUID, accountId: UUID) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/accounts/$accountId") {
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
