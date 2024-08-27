package ro.jf.bk.fund.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.TransactionApi
import ro.jf.bk.fund.api.exception.FundApiException
import ro.jf.bk.fund.api.model.TransactionTO
import java.util.*

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
            throw FundApiException.Generic()
        }
        val transactions = response.body<ListTO<TransactionTO>>()
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