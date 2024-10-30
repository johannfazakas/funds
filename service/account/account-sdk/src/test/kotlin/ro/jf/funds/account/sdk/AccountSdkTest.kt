package ro.jf.funds.account.sdk

import kotlinx.coroutines.runBlocking
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
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class AccountSdkTest {
    private val accountSdk = AccountSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `test list accounts`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = AccountName("Cash")

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
                                    put("name", JsonPrimitive(accountName.value))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("RON"))
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val accounts = accountSdk.listAccounts(userId)

        assertThat(accounts.items).hasSize(1)
        assertThat(accounts.items.first()).isInstanceOf(AccountTO::class.java)
        val currencyAccount = accounts.items.first()
        assertThat(currencyAccount.id).isEqualTo(accountId)
        assertThat(currencyAccount.name).isEqualTo(accountName)
        assertThat(currencyAccount.unit).isEqualTo(Currency.RON)
    }

    @Test
    fun `test find account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()
        val accountName = AccountName("BT")

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
                            put("name", JsonPrimitive(accountName.value))
                            put("type", JsonPrimitive("instrument"))
                            put("unit", buildJsonObject {
                                put("type", JsonPrimitive("symbol"))
                                put("value", JsonPrimitive("ICBETNETF"))
                            })
                        }.toString()
                    )
            )

        val account = accountSdk.findAccountById(userId, accountId)

        assertThat(account).isInstanceOf(AccountTO::class.java)
        val instrumentAccount = account as AccountTO
        assertThat(account).isNotNull
        assertThat(account.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(accountName)
        assertThat(instrumentAccount.unit).isEqualTo(Symbol("ICBETNETF"))
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
        val accountName = AccountName("BT")

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/account/v1/accounts")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
                    .withBody(
                        jsonSchema(
                            buildJsonObject {
                                put("type", JsonPrimitive("object"))
                                put(
                                    "properties", buildJsonObject {
                                        put("name", buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put("pattern", JsonPrimitive(accountName.value))
                                        })
                                    }
                                )
                                put("required", buildJsonArray {
                                    add(JsonPrimitive("name"))
                                    add(JsonPrimitive("unit"))
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
                            put("type", JsonPrimitive("currency"))
                            put("name", JsonPrimitive(accountName.value))
                            put("unit", buildJsonObject {
                                put("type", JsonPrimitive("currency"))
                                put("value", JsonPrimitive("RON"))
                            })
                        }.toString()
                    )
            )

        val account = accountSdk.createAccount(userId, CreateAccountTO(accountName, Currency.RON))

        assertThat(account).isNotNull
        assertThat(account.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(accountName)
        assertThat(account.unit).isEqualTo(Currency.RON)
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
