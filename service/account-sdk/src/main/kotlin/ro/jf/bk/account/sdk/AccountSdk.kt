package ro.jf.bk.account.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.AccountApi
import ro.jf.bk.account.api.exception.AccountApiException
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.*
import ro.jf.funds.commons.sdk.createHttpClient

private const val LOCALHOST_BASE_URL = "http://localhost:5211"
private const val BASE_PATH = "/bk-api/account/v1"

private val log = logger { }

class AccountSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : AccountApi {
    override suspend fun listAccounts(userId: UUID): List<AccountTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/accounts") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            return emptyList()
        }
        val accounts = response.body<ListTO<AccountTO>>()
        log.debug { "Retrieved accounts: $accounts" }
        return accounts.items
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
                log.warn { "Unexpected response on find account by id: $response" }
                throw AccountApiException.Generic(response)
            }
        }
    }

    override suspend fun createAccount(userId: UUID, request: CreateCurrencyAccountTO): AccountTO.Currency {
        val response = httpClient.post("$baseUrl$BASE_PATH/accounts/currency") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return when (response.status) {
            HttpStatusCode.Created -> response.body()
            HttpStatusCode.Conflict -> throw AccountApiException.AccountNameAlreadyExists(request.name)
            else -> {
                log.warn { "Unexpected response on create currency account: $response" }
                throw AccountApiException.Generic(response)
            }
        }
    }

    override suspend fun createAccount(userId: UUID, request: CreateInstrumentAccountTO): AccountTO.Instrument {
        val response = httpClient.post("$baseUrl$BASE_PATH/accounts/instrument") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return when (response.status) {
            HttpStatusCode.Created -> response.body()
            HttpStatusCode.Conflict -> throw AccountApiException.AccountNameAlreadyExists(request.name)
            else -> {
                log.warn { "Unexpected response on create instrument account: $response" }
                throw AccountApiException.Generic(response)
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
            throw AccountApiException.Generic(response)
        }
    }
}
