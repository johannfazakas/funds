package ro.jf.bk.account.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.AccountTransactionApi
import ro.jf.bk.account.api.exception.AccountApiException
import ro.jf.bk.account.api.model.CreateAccountTransactionTO
import ro.jf.bk.account.api.model.AccountTransactionTO
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5211"
private const val BASE_PATH = "/bk-api/account/v1"

private val log = logger { }

class AccountTransactionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient
) : AccountTransactionApi {
    override suspend fun createTransaction(userId: UUID, request: CreateAccountTransactionTO): AccountTransactionTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.warn { "Unexpected response on create transaction: $response" }
            throw AccountApiException.Generic()
        }
        val accountTransaction = response.body<AccountTransactionTO>()
        log.debug { "Created account transaction: $accountTransaction" }
        return accountTransaction
    }

    override suspend fun listTransactions(userId: UUID): List<AccountTransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            throw AccountApiException.Generic()
        }
        val transactions = response.body<ListTO<AccountTransactionTO>>()
        log.debug { "Retrieved transactions: $transactions" }
        return transactions.items
    }

    override suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/transactions/$transactionId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (!response.status.isSuccess()) {
            log.warn { "Unexpected response on delete transaction: $response" }
            throw AccountApiException.Generic()
        }
    }
}