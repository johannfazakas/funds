package ro.jf.funds.fund.sdk

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
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class FundTransactionSdkTest {
    private val fundTransactionSdk = FundTransactionSdk(
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

    private val userId = randomUUID()
    private val transactionId = randomUUID()
    private val transactionExternalId = randomUUID().toString()
    private val dateTime = "2024-07-22T09:17"
    private val recordId = randomUUID()
    private val accountId = randomUUID()
    private val fundId = randomUUID()

    @Test
    fun `given create transaction`(mockServerClient: MockServerClient): Unit = runBlocking {
        val amount = 42.0

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
                            put("externalId", JsonPrimitive(transactionExternalId))
                            put("dateTime", JsonPrimitive(dateTime))
                            put("records", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(recordId.toString()))
                                    put("accountId", JsonPrimitive(accountId.toString()))
                                    put("fundId", JsonPrimitive(fundId.toString()))
                                    put("amount", JsonPrimitive(amount))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("RON"))
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transaction = fundTransactionSdk.createTransaction(
            userId,
            CreateFundTransactionTO(
                dateTime = LocalDateTime.parse(dateTime),
                externalId = transactionExternalId,
                records = listOf(
                    CreateFundRecordTO(
                        accountId = accountId,
                        fundId = fundId,
                        amount = BigDecimal(amount),
                        unit = Currency.RON
                    )
                )
            )
        )

        assertThat(transaction).isInstanceOf(FundTransactionTO::class.java)
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(1)
        assertThat(transaction.records.first().id).isEqualTo(recordId)
        assertThat(transaction.records.first().accountId).isEqualTo(accountId)
        assertThat(transaction.records.first().amount.compareTo(BigDecimal(amount))).isZero()
        assertThat(transaction.records.first().fundId).isEqualTo(fundId)
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
                                    put(
                                        "dateTime",
                                        JsonPrimitive(dateTime)
                                    )
                                    put("externalId", JsonPrimitive(transactionExternalId))
                                    put("records", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", JsonPrimitive(recordId.toString()))
                                            put("accountId", JsonPrimitive(accountId.toString()))
                                            put("fundId", JsonPrimitive(fundId.toString()))
                                            put("amount", JsonPrimitive(42.0))
                                            put("unit", buildJsonObject {
                                                put("type", JsonPrimitive("currency"))
                                                put("value", JsonPrimitive("RON"))
                                            })
                                        })
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transactions = fundTransactionSdk.listTransactions(userId)

        assertThat(transactions.items).hasSize(1)
        assertThat(transactions.items.first()).isInstanceOf(FundTransactionTO::class.java)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(1)
        assertThat(transaction.records.first().id).isEqualTo(recordId)
        assertThat(transaction.records.first().accountId).isEqualTo(accountId)
        assertThat(transaction.records.first().amount.compareTo(BigDecimal(42.0))).isZero()
        assertThat(transaction.records.first().fundId).isEqualTo(fundId)
    }

    @Test
    fun `given list fund transactions`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/funds/$fundId/transactions")
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
                                    put("externalId", JsonPrimitive(transactionExternalId))
                                    put(
                                        "dateTime",
                                        JsonPrimitive(dateTime)
                                    )
                                    put("records", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", JsonPrimitive(recordId.toString()))
                                            put("accountId", JsonPrimitive(accountId.toString()))
                                            put("fundId", JsonPrimitive(fundId.toString()))
                                            put("amount", JsonPrimitive(42.0))
                                            put("unit", buildJsonObject {
                                                put("type", JsonPrimitive("currency"))
                                                put("value", JsonPrimitive("RON"))
                                            })
                                        })
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transactions = fundTransactionSdk.listTransactions(userId, fundId)

        assertThat(transactions.items).hasSize(1)
        assertThat(transactions.items.first()).isInstanceOf(FundTransactionTO::class.java)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(1)
        assertThat(transaction.records.first().id).isEqualTo(recordId)
        assertThat(transaction.records.first().accountId).isEqualTo(accountId)
        assertThat(transaction.records.first().amount.compareTo(BigDecimal(42.0))).isZero()
        assertThat(transaction.records.first().fundId).isEqualTo(fundId)
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

        fundTransactionSdk.deleteTransaction(userId, transactionId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}