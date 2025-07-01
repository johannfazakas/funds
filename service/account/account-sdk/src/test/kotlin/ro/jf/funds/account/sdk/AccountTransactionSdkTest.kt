package ro.jf.funds.account.sdk

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
import org.mockserver.model.JsonSchemaBody.jsonSchema
import org.mockserver.model.MediaType
import ro.jf.funds.account.api.model.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class AccountTransactionSdkTest {
    private val accountTransactionSdk = AccountTransactionSdk(
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

    @Test
    fun `test create transaction`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val rawTransactionDateTime = "2021-09-01T12:00:00"
        val transactionDateTime = LocalDateTime.parse("2021-09-01T12:00:00")
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val transactionId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/funds-api/account/v1/transactions")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
                    .withBody(
                        jsonSchema(
                            buildJsonObject {
                                put("required", buildJsonArray {
                                    add(JsonPrimitive("dateTime"))
                                    add(JsonPrimitive("records"))
                                    add(JsonPrimitive("properties"))
                                })
                                put("type", JsonPrimitive("object"))
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("dateTime", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                        })
                                        put("properties", buildJsonArray {
                                            add(buildJsonObject {
                                                put("key", JsonPrimitive("type"))
                                                put("value", JsonPrimitive("object"))
                                            })
                                        })
                                        put("records", buildJsonObject {
                                            put("type", JsonPrimitive("array"))
                                        })
                                    }
                                )
                            }.toString()
                        )
                    )
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(transactionId.toString()))
                            put("userId", JsonPrimitive(userId.toString()))
                            put("dateTime", JsonPrimitive(rawTransactionDateTime))
                            put("records", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(recordId1.toString()))
                                    put("accountId", JsonPrimitive(randomUUID().toString()))
                                    put("amount", JsonPrimitive(100.25))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("RON"))
                                    })
                                    put("labels", buildJsonArray {
                                        add(JsonPrimitive("one"))
                                        add(JsonPrimitive("two"))
                                    })
                                    put("properties", buildJsonArray {
                                        add(buildJsonObject {
                                            put("key", JsonPrimitive("external_id"))
                                            put("value", JsonPrimitive("1111"))
                                        })
                                    })
                                })
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(recordId2.toString()))
                                    put("accountId", JsonPrimitive(randomUUID().toString()))
                                    put("amount", JsonPrimitive(-100.25))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("RON"))
                                    })
                                    put("labels", buildJsonArray { })
                                    put("properties", buildJsonArray {
                                        add(buildJsonObject {
                                            put("key", JsonPrimitive("external_id"))
                                            put("value", JsonPrimitive("2222"))
                                        })
                                    })
                                })
                            })
                            put("properties", buildJsonArray {
                                add(buildJsonObject {
                                    put("key", JsonPrimitive("key"))
                                    put("value", JsonPrimitive("value"))
                                })
                            })
                        }.toString()
                    )
            )
        val createTransactionRequest = CreateAccountTransactionTO(
            dateTime = transactionDateTime,
            records = listOf(
                CreateAccountRecordTO(
                    randomUUID(), BigDecimal("100.25"), Currency.RON,
                    listOf(Label("one"), Label("two")), propertiesOf("external_id" to "1111")
                ),
                CreateAccountRecordTO(
                    randomUUID(), BigDecimal("-100.25"), Currency.RON,
                    emptyList(), propertiesOf("external_id" to "2222")
                )
            ),
            properties = propertiesOf("key" to "value")
        )

        val transaction = accountTransactionSdk.createTransaction(userId, createTransactionRequest)

        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime).isEqualTo(transactionDateTime)
        assertThat(transaction.records).hasSize(2)
        assertThat(transaction.records[0].id).isEqualTo(recordId1)
        assertThat(transaction.records[0].accountId).isNotEqualTo(accountId1)
        assertThat(transaction.records[0].amount).isEqualTo(BigDecimal("100.25"))
        assertThat(transaction.records[0].properties).isEqualTo(propertiesOf("external_id" to "1111"))
        assertThat(transaction.records[1].id).isEqualTo(recordId2)
        assertThat(transaction.records[1].accountId).isNotEqualTo(accountId2)
        assertThat(transaction.records[1].amount).isEqualTo(BigDecimal("-100.25"))
        assertThat(transaction.records[1].properties).isEqualTo(propertiesOf("external_id" to "2222"))
    }

    @Test
    fun `test list transactions`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        val recordId = randomUUID()
        val accountId = randomUUID()
        val dateTime = "2024-07-22T09:17"
        val filter = AccountTransactionFilterTO(
            transactionProperties = propertiesOf("transactionProp" to "value1", "transactionProp" to "value2"),
            recordProperties = propertiesOf("recordProp" to "value3")
        )

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/account/v1/transactions")
                    .withQueryStringParameters(
                        mapOf(
                            "${TRANSACTION_PROPERTIES_PREFIX}transactionProp" to listOf("value1", "value2"),
                            "${RECORD_PROPERTIES_PREFIX}recordProp" to listOf("value3")
                        )
                    )
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
                                            put("unit", buildJsonObject {
                                                put("type", JsonPrimitive("currency"))
                                                put("value", JsonPrimitive("RON"))
                                            })
                                            put("labels", buildJsonArray {
                                                add(JsonPrimitive("one"))
                                                add(JsonPrimitive("two"))
                                            })
                                            put("properties", buildJsonArray {
                                                add(buildJsonObject {
                                                    put("key", JsonPrimitive("recordProp"))
                                                    put("value", JsonPrimitive("value3"))
                                                })
                                            })
                                        })
                                    })
                                    put("properties", buildJsonArray {
                                        add(buildJsonObject {
                                            put("key", JsonPrimitive("transactionProp"))
                                            put("value", JsonPrimitive("value1"))
                                        })
                                        add(buildJsonObject {
                                            put("key", JsonPrimitive("transactionProp"))
                                            put("value", JsonPrimitive("value2"))
                                        })
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val transactions = accountTransactionSdk.listTransactions(userId, filter)

        assertThat(transactions.items).hasSize(1)
        assertThat(transactions.items.first()).isInstanceOf(AccountTransactionTO::class.java)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime.toString()).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(1)
        assertThat(transaction.records.first().id).isEqualTo(recordId)
        assertThat(transaction.records.first().accountId).isEqualTo(accountId)
        assertThat(transaction.records.first().amount.compareTo(BigDecimal(42.0))).isZero()
        assertThat(transaction.records.first().properties.single { it.key == "recordProp" }.value).isEqualTo("value3")
        assertThat(transaction.properties).hasSize(2)
        assertThat(transaction.properties.filter { it.key == "transactionProp" }
            .map { it.value }).isEqualTo(listOf("value1", "value2"))
    }

    @Test
    fun `test remove transaction by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/account/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(204))

        accountTransactionSdk.deleteTransaction(userId, transactionId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/account/v1/transactions/$transactionId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}
