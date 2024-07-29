package ro.jf.bk.account.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.AccountApi
import ro.jf.bk.account.api.TransactionApi
import ro.jf.bk.account.api.exception.AccountApiException
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5211"
private const val BASE_PATH = "/bk-api/account/v1"

private val log = logger { }

class TransactionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient
) : TransactionApi {
    override suspend fun listTransactions(userId: UUID): List<TransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            throw AccountApiException.Generic()
        }
        val transactions = response.body<ListTO<TransactionTO>>()
        log.debug { "Retrieved transactions: $transactions" }
        return transactions.items
    }
}