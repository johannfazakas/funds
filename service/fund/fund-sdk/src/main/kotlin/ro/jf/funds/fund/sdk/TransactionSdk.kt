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
import ro.jf.funds.fund.api.TransactionApi
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionTO

private val log = logger { }

class TransactionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : TransactionApi {
    override suspend fun createTransaction(userId: Uuid, transaction: CreateTransactionTO): TransactionTO =
        withSuspendingSpan {
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
            val fundTransaction = response.body<TransactionTO>()
            log.debug { "Created fund transaction: $fundTransaction" }
            fundTransaction
        }

    override suspend fun listTransactions(
        userId: Uuid,
        filter: TransactionFilterTO,
    ): ListTO<TransactionTO> = withSuspendingSpan {
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
        val transactions = response.body<ListTO<TransactionTO>>()
        log.debug { "Retrieved transactions: $transactions" }
        transactions
    }

    override suspend fun deleteTransaction(userId: Uuid, transactionId: Uuid) = withSuspendingSpan {
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
