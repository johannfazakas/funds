package ro.jf.funds.fund.sdk

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
import org.mockserver.model.MediaType
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.CreateAccountTO
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class FundAccountSdkTest {
    private val fundAccountSdk = FundAccountSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `test list accounts`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/accounts")
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
                                    put("id", JsonPrimitive(accountId1.toString()))
                                    put("name", JsonPrimitive("Checking Account"))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("RON"))
                                    })
                                })
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(accountId2.toString()))
                                    put("name", JsonPrimitive("Savings Account"))
                                    put("unit", buildJsonObject {
                                        put("type", JsonPrimitive("currency"))
                                        put("value", JsonPrimitive("EUR"))
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val accounts = fundAccountSdk.listAccounts(userId)

        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items[0].id).isEqualTo(accountId1)
        assertThat(accounts.items[0].name).isEqualTo(AccountName("Checking Account"))
        assertThat(accounts.items[0].unit).isEqualTo(ro.jf.funds.commons.model.Currency.RON)
        assertThat(accounts.items[1].id).isEqualTo(accountId2)
        assertThat(accounts.items[1].name).isEqualTo(AccountName("Savings Account"))
        assertThat(accounts.items[1].unit).isEqualTo(ro.jf.funds.commons.model.Currency.EUR)
    }

    @Test
    fun `test get account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(accountId.toString()))
                            put("name", JsonPrimitive("Investment Account"))
                            put("unit", buildJsonObject {
                                put("type", JsonPrimitive("currency"))
                                put("value", JsonPrimitive("USD"))
                            })
                        }.toString()
                    )
            )

        val account = fundAccountSdk.findAccountById(userId, accountId)

        assertThat(account).isNotNull
        assertThat(account!!.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(AccountName("Investment Account"))
        assertThat(account.unit).isEqualTo(ro.jf.funds.commons.model.Currency.USD)
    }

    @Test
    fun `test get account by id not found`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(404))

        val account = fundAccountSdk.findAccountById(userId, accountId)

        assertThat(account).isNull()
    }

    @Test
    fun `test create account`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/funds-api/fund/v1/accounts")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(accountId.toString()))
                            put("name", JsonPrimitive("New Checking Account"))
                            put("unit", buildJsonObject {
                                put("type", JsonPrimitive("currency"))
                                put("value", JsonPrimitive("RON"))
                            })
                        }.toString()
                    )
            )

        val account = fundAccountSdk.createAccount(
            userId,
            CreateAccountTO(
                name = AccountName("New Checking Account"),
                unit = ro.jf.funds.commons.model.Currency.RON
            )
        )

        assertThat(account.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(AccountName("New Checking Account"))
        assertThat(account.unit).isEqualTo(ro.jf.funds.commons.model.Currency.RON)
    }

    @Test
    fun `test delete account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(204))

        fundAccountSdk.deleteAccountById(userId, accountId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}