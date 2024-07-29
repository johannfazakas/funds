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
import org.mockserver.model.JsonSchemaBody.jsonSchema
import org.mockserver.model.MediaType
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.UUID.randomUUID

@ExtendWith(MockServerExtension::class)
class AccountSdkTest {
    private val accountSdk = AccountSdk(
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
    fun `test list accounts`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = "Cash"

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/account/v1/accounts")
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
                                    put("id", JsonPrimitive(accountId.toString()))
                                    put("name", JsonPrimitive(accountName))
                                    put("type", JsonPrimitive("currency"))
                                    put("currency", JsonPrimitive("RON"))
                                })
                            })
                        }.toString()
                    )
            )

        val accounts = accountSdk.listAccounts(userId)

        assertThat(accounts).hasSize(1)
        assertThat(accounts.first()).isInstanceOf(AccountTO.Currency::class.java)
        val currencyAccount = accounts.first() as AccountTO.Currency
        assertThat(currencyAccount.id).isEqualTo(accountId)
        assertThat(currencyAccount.name).isEqualTo(accountName)
        assertThat(currencyAccount.currency).isEqualTo("RON")
    }

    @Test
    fun `test find account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = "BT"

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/account/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(accountId.toString()))
                            put("name", JsonPrimitive(accountName))
                            put("type", JsonPrimitive("instrument"))
                            put("currency", JsonPrimitive("RON"))
                            put("symbol", JsonPrimitive("ICBETNETF"))
                        }.toString()
                    )
            )

        val account = accountSdk.findAccountById(userId, accountId)

        assertThat(account).isInstanceOf(AccountTO.Instrument::class.java)
        val instrumentAccount = account as AccountTO.Instrument
        assertThat(account).isNotNull
        assertThat(account.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(accountName)
        assertThat(instrumentAccount.currency).isEqualTo("RON")
        assertThat(instrumentAccount.symbol).isEqualTo("ICBETNETF")
    }

    @Test
    fun `test find account by id when not found`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/account/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(404)
            )

        val account = accountSdk.findAccountById(userId, accountId)

        assertThat(account).isNull()
    }

    @Test
    fun `test create currency account`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = "BT"

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/account/v1/accounts/currency")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
                    .withBody(
                        jsonSchema(
                            buildJsonObject {
                                put("type", JsonPrimitive("object"))
                                put(
                                    "properties", buildJsonObject {
                                        put("name", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive(accountName))
                                        })
                                        put("currency", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive("RON"))
                                        })
                                    }
                                )
                                put("required", buildJsonArray {
                                    add(JsonPrimitive("name"))
                                    add(JsonPrimitive("currency"))
                                })
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
                            put("id", JsonPrimitive(accountId.toString()))
                            put("name", JsonPrimitive(accountName))
                            put("currency", JsonPrimitive("RON"))
                        }.toString()
                    )
            )

        val account = accountSdk.createAccount(userId, CreateCurrencyAccountTO(accountName, "RON"))

        val currencyAccount = account as AccountTO.Currency
        assertThat(currencyAccount).isNotNull
        assertThat(currencyAccount.id).isEqualTo(accountId)
        assertThat(currencyAccount.name).isEqualTo(accountName)
        assertThat(currencyAccount.currency).isEqualTo("RON")
    }

    @Test
    fun `test create instrument account`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = "BT"

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/account/v1/accounts/instrument")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
                    .withBody(
                        jsonSchema(
                            buildJsonObject {
                                put("type", JsonPrimitive("object"))
                                put(
                                    "properties", buildJsonObject {
                                        put("name", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive(accountName))
                                        })
                                        put("currency", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive("RON"))
                                        })
                                        put("symbol", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive("ICBETNETF"))
                                        })
                                    }
                                )
                                put("required", buildJsonArray {
                                    add(JsonPrimitive("name"))
                                    add(JsonPrimitive("currency"))
                                    add(JsonPrimitive("symbol"))
                                })
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
                            put("id", JsonPrimitive(accountId.toString()))
                            put("name", JsonPrimitive(accountName))
                            put("currency", JsonPrimitive("RON"))
                            put("symbol", JsonPrimitive("ICBETNETF"))
                            put("type", JsonPrimitive("instrument"))
                        }.toString()
                    )
            )

        val account =
            accountSdk.createAccount(userId, CreateInstrumentAccountTO(accountName, "RON", "ICBETNETF"))

        val instrumentAccount = account as AccountTO.Instrument
        assertThat(instrumentAccount).isNotNull
        assertThat(instrumentAccount.id).isEqualTo(accountId)
        assertThat(instrumentAccount.name).isEqualTo(accountName)
        assertThat(instrumentAccount.currency).isEqualTo("RON")
        assertThat(instrumentAccount.symbol).isEqualTo("ICBETNETF")
    }

    @Test
    fun `test delete account`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/bk-api/account/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(204)
            )

        accountSdk.deleteAccountById(userId, accountId)

        mockServerClient.verify(
            request()
                .withMethod("DELETE")
                .withPath("/bk-api/account/v1/accounts/$accountId")
                .withHeader(Header(USER_ID_HEADER, userId.toString()))
        )
    }
}
