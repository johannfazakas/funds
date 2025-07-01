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
import ro.jf.funds.fund.api.FundTransactionApi
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import java.util.*

private val log = logger { }

class FundTransactionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
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
            throw response.toApiException()
        }
        val fundTransaction = response.body<FundTransactionTO>()
        log.debug { "Created fund transaction: $fundTransaction" }
        return fundTransaction
    }

    override suspend fun listTransactions(
        userId: UUID,
        fundId: UUID,
        filter: FundTransactionFilterTO,
    ): ListTO<FundTransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/funds/$fundId/transactions") {
            filter.fromDate?.let { parameter("fromDate", it.toString()) }
            filter.toDate?.let { parameter("toDate", it.toString()) }
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list fund transactions: $response" }
            throw response.toApiException()
        }
        val transactions = response.body<ListTO<FundTransactionTO>>()
        log.debug { "Retrieved fund $fundId transactions: $transactions" }
        return transactions
    }

    override suspend fun listTransactions(
        userId: UUID,
        filter: FundTransactionFilterTO,
    ): ListTO<FundTransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/transactions") {
            filter.fromDate?.let { parameter("fromDate", it.toString()) }
            filter.toDate?.let { parameter("toDate", it.toString()) }
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list transactions: $response" }
            throw response.toApiException()
        }
        val transactions = response.body<ListTO<FundTransactionTO>>()
        log.debug { "Retrieved transactions: $transactions" }
        return transactions
    }

    override suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/transactions/$transactionId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (!response.status.isSuccess()) {
            log.warn { "Unexpected response on delete transaction: $response" }
            throw response.toApiException()
        }
    }
}
