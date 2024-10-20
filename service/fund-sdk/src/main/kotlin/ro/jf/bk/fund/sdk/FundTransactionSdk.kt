package ro.jf.bk.fund.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.FundTransactionApi
import ro.jf.bk.fund.api.exception.FundApiException
import ro.jf.bk.fund.api.model.CreateFundTransactionTO
import ro.jf.bk.fund.api.model.FundTransactionTO
import java.util.*

private val log = logger { }

class FundTransactionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient
) : FundTransactionApi {
    override suspend fun createTransaction(userId: UUID, transaction: CreateFundTransactionTO): FundTransactionTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(transaction)
        }
        if (response.status != HttpStatusCode.Created) {
            log.warn { "Unexpected response on create transaction: $response" }
            throw FundApiException.Generic()
        }
        val fundTransaction = response.body<FundTransactionTO>()
        log.debug { "Created fund transaction: $fundTransaction" }
        return fundTransaction
    }

    override suspend fun listTransactions(userId: UUID): List<FundTransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list accounts: $response" }
            throw FundApiException.Generic()
        }
        val transactions = response.body<ListTO<FundTransactionTO>>()
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
            throw FundApiException.Generic()
        }
    }
}
