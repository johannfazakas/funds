package ro.jf.bk.account.sdk

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
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
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(MockServerExtension::class)
class TransactionSdkTest {
    private val transactionSdk = TransactionSdk(
        baseUrl = MockServerExtension.baseUrl,
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

    @Test
    fun `test list transactions`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        val recordId = randomUUID()
        val accountId = randomUUID()
        val dateTime = "2024-07-22T09:17"

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/account/v1/transactions")
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
                                    put(
                                        "dateTime",
                                        JsonPrimitive(dateTime)
                                    )
                                    put("records", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", JsonPrimitive(recordId.toString()))
                                            put("accountId", JsonPrimitive(accountId.toString()))
                                            put("amount", JsonPrimitive(42.0))
                                            put("metadata", buildJsonObject {
                                                put("external_id", JsonPrimitive("4321"))
                                            })
                                        })
                                    })
                                    put("metadata", buildJsonObject {
                                        put("external_id", JsonPrimitive("1234"))
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transactions = transactionSdk.listTransactions(userId)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first()).isInstanceOf(TransactionTO::class.java)
        val transaction = transactions.first()
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(1)
        assertThat(transaction.records.first().id).isEqualTo(recordId)
        assertThat(transaction.records.first().accountId).isEqualTo(accountId)
        assertThat(transaction.records.first().amount.compareTo(BigDecimal(42.0))).isZero()
        assertThat(transaction.records.first().metadata["external_id"]).isEqualTo("4321")
        assertThat(transaction.metadata).hasSize(1)
        assertThat(transaction.metadata["external_id"]).isEqualTo("1234")
    }

    @Test
    fun `test remove transaction by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/bk-api/account/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(204))

        transactionSdk.deleteTransaction(userId, transactionId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/bk-api/account/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}
