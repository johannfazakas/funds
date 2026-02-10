package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.fund.api.model.AccountSortField
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest

private const val LOCALHOST_BASE_URL = "http://localhost:5253"
private const val BASE_PATH = "/funds-api/fund/v1"

class AccountClient(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("AccountClient")

    suspend fun listAccounts(
        userId: Uuid,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<AccountSortField>? = null,
    ): PageTO<AccountTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/accounts") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            url {
                parameters.appendPageRequest(pageRequest)
                parameters.appendSortRequest(sortRequest)
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list accounts: $response" }
            throw Exception("Failed to list accounts: ${response.status}")
        }
        val accounts = response.body<PageTO<AccountTO>>()
        log.d { "Retrieved accounts: $accounts" }
        return accounts
    }

    suspend fun createAccount(userId: Uuid, request: CreateAccountTO): AccountTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/accounts") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.w { "Unexpected response on create account: $response" }
            throw Exception("Failed to create account: ${response.status}")
        }
        return response.body()
    }

    suspend fun deleteAccount(userId: Uuid, accountId: Uuid) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/accounts/$accountId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.w { "Unexpected response on delete account: $response" }
            throw Exception("Failed to delete account: ${response.status}")
        }
    }
}
