package ro.jf.funds.fund.sdk

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionTO

@ExtendWith(MockServerContainerExtension::class)
class TransactionSdkTest {
    private val transactionSdk = TransactionSdk(
        baseUrl = MockServerContainerExtension.baseUrl,
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    )

    private val userId = uuid4()
    private val transactionId = uuid4()
    private val transactionExternalId = uuid4().toString()
    private val dateTime = "2024-07-22T09:17"
    private val recordId = uuid4()
    private val accountId = uuid4()
    private val fundId = uuid4()

    @Test
    fun `given create transaction`(mockServerClient: MockServerClient): Unit = runBlocking {
        val amount = BigDecimal.parseString("42.0")

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/funds-api/fund/v1/transactions")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(transactionId.toString()))
                            put("userId", JsonPrimitive(userId.toString()))
                            put("type", JsonPrimitive("SINGLE_RECORD"))
                            put("externalId", JsonPrimitive(transactionExternalId))
                            put("dateTime", JsonPrimitive(dateTime))
                            put("record", buildJsonObject {
                                put("type", JsonPrimitive("CURRENCY"))
                                put("id", JsonPrimitive(recordId.toString()))
                                put("accountId", JsonPrimitive(accountId.toString()))
                                put("fundId", JsonPrimitive(fundId.toString()))
                                put("amount", JsonPrimitive(amount.toStringExpanded()))
                                put("unit", JsonPrimitive("RON"))
                                put("labels", buildJsonArray {})
                            })
                        }.toString()
                    )
            )

        val transaction = transactionSdk.createTransaction(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = LocalDateTime.parse(dateTime),
                externalId = transactionExternalId,
                record = CreateTransactionRecordTO.CurrencyRecord(
                    accountId = accountId,
                    fundId = fundId,
                    amount = amount,
                    unit = Currency.RON,
                )
            )
        )

        assertThat(transaction).isInstanceOf(TransactionTO.SingleRecord::class.java)
        val singleRecord = transaction as TransactionTO.SingleRecord
        assertThat(singleRecord.id).isEqualTo(transactionId)
        assertThat(singleRecord.dateTime.toString()).isEqualTo(dateTime)
        assertThat(singleRecord.record.id).isEqualTo(recordId)
        assertThat(singleRecord.record.accountId).isEqualTo(accountId)
        assertThat(singleRecord.record.amount).isEqualTo(amount)
        assertThat(singleRecord.record.fundId).isEqualTo(fundId)
    }

    @Test
    fun `given list transactions`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/transactions")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("items", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(transactionId.toString()))
                                    put("userId", JsonPrimitive(userId.toString()))
                                    put("type", JsonPrimitive("SINGLE_RECORD"))
                                    put("dateTime", JsonPrimitive(dateTime))
                                    put("externalId", JsonPrimitive(transactionExternalId))
                                    put("record", buildJsonObject {
                                        put("type", JsonPrimitive("CURRENCY"))
                                        put("id", JsonPrimitive(recordId.toString()))
                                        put("accountId", JsonPrimitive(accountId.toString()))
                                        put("fundId", JsonPrimitive(fundId.toString()))
                                        put("amount", JsonPrimitive(42.0))
                                        put("unit", JsonPrimitive("RON"))
                                        put("labels", buildJsonArray {})
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transactions = transactionSdk.listTransactions(userId)

        assertThat(transactions.items).hasSize(1)
        assertThat(transactions.items.first()).isInstanceOf(TransactionTO.SingleRecord::class.java)
        val transaction = transactions.items.first() as TransactionTO.SingleRecord
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.record.id).isEqualTo(recordId)
        assertThat(transaction.record.accountId).isEqualTo(accountId)
        assertThat(transaction.record.amount).isEqualTo(42.0)
        assertThat(transaction.record.fundId).isEqualTo(fundId)
    }

    @Test
    fun `given remove transaction by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(204))

        transactionSdk.deleteTransaction(userId, transactionId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}