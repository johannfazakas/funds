package ro.jf.funds.fund.sdk

import com.benasher44.uuid.uuid4
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
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.CreateAccountTO

@ExtendWith(MockServerContainerExtension::class)
class AccountSdkTest {
    private val accountSdk = AccountSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `test list accounts`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val accountId1 = uuid4()
        val accountId2 = uuid4()

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

        val accounts = accountSdk.listAccounts(userId)

        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items[0].id).isEqualTo(accountId1)
        assertThat(accounts.items[0].name).isEqualTo(AccountName("Checking Account"))
        assertThat(accounts.items[0].unit).isEqualTo(Currency.RON)
        assertThat(accounts.items[1].id).isEqualTo(accountId2)
        assertThat(accounts.items[1].name).isEqualTo(AccountName("Savings Account"))
        assertThat(accounts.items[1].unit).isEqualTo(Currency.EUR)
    }

    @Test
    fun `test get account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val accountId = uuid4()

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

        val account = accountSdk.findAccountById(userId, accountId)

        assertThat(account).isNotNull
        assertThat(account!!.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(AccountName("Investment Account"))
        assertThat(account.unit).isEqualTo(Currency.USD)
    }

    @Test
    fun `test get account by id not found`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val accountId = uuid4()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(404))

        val account = accountSdk.findAccountById(userId, accountId)

        assertThat(account).isNull()
    }

    @Test
    fun `test create account`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val accountId = uuid4()

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

        val account = accountSdk.createAccount(
            userId,
            CreateAccountTO(
                name = AccountName("New Checking Account"),
                unit = Currency.RON
            )
        )

        assertThat(account.id).isEqualTo(accountId)
        assertThat(account.name).isEqualTo(AccountName("New Checking Account"))
        assertThat(account.unit).isEqualTo(Currency.RON)
    }

    @Test
    fun `test delete account by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val accountId = uuid4()

        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(response().withStatusCode(204))

        accountSdk.deleteAccountById(userId, accountId)

        mockServerClient
            .verify(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/fund/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
    }
}