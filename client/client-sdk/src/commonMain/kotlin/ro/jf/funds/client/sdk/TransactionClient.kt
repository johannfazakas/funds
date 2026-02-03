package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.platform.api.model.ListTO

private const val LOCALHOST_BASE_URL = "http://localhost:5253"
private const val BASE_PATH = "/funds-api/fund/v1"

class TransactionClient(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("TransactionClient")

    suspend fun listTransactions(userId: Uuid, filter: TransactionFilterTO = TransactionFilterTO.empty()): List<TransactionTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/transactions") {
            filter.fromDate?.let { parameter("fromDate", it.toString()) }
            filter.toDate?.let { parameter("toDate", it.toString()) }
            filter.fundId?.let { parameter("fundId", it.toString()) }
            filter.accountId?.let { parameter("accountId", it.toString()) }
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list transactions: $response" }
            throw Exception("Failed to list transactions: ${response.status}")
        }
        val transactions = response.body<ListTO<TransactionTO>>()
        log.d { "Retrieved transactions: $transactions" }
        return transactions.items
    }

    suspend fun createTransaction(userId: Uuid, request: CreateTransactionTO): TransactionTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/transactions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.w { "Unexpected response on create transaction: $response" }
            throw Exception("Failed to create transaction: ${response.status}")
        }
        return response.body()
    }

    suspend fun deleteTransaction(userId: Uuid, transactionId: Uuid) {
        val response = httpClient.delete("$baseUrl$BASE_PATH/transactions/$transactionId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.w { "Unexpected response on delete transaction: $response" }
            throw Exception("Failed to delete transaction: ${response.status}")
        }
    }
}
