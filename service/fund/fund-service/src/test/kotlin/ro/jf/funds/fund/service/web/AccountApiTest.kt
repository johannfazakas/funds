package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName as ExternalAccountName
import ro.jf.funds.account.api.model.AccountTO as ExternalAccountTO
import ro.jf.funds.account.api.model.CreateAccountTO as ExternalCreateAccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.service.config.configureFundErrorHandling
import ro.jf.funds.fund.service.config.configureFundRouting
import ro.jf.funds.fund.service.config.fundDependencies
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountApiTest {
    private val accountSdk = mock<AccountSdk>()

    @Test
    fun `test list accounts`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, appConfig)

        val userId = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()

        val externalAccounts = ListTO(
            listOf(
                ExternalAccountTO(accountId1, ExternalAccountName("Checking Account"), Currency.RON),
                ExternalAccountTO(accountId2, ExternalAccountName("Savings Account"), Currency.EUR)
            )
        )

        whenever(accountSdk.listAccounts(userId)).thenReturn(externalAccounts)

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val accounts = response.body<ListTO<AccountTO>>()
        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items[0].name).isEqualTo(AccountName("Checking Account"))
        assertThat(accounts.items[0].unit).isEqualTo(Currency.RON)
        assertThat(accounts.items[0].id).isEqualTo(accountId1)
        assertThat(accounts.items[1].name).isEqualTo(AccountName("Savings Account"))
        assertThat(accounts.items[1].unit).isEqualTo(Currency.EUR)
        assertThat(accounts.items[1].id).isEqualTo(accountId2)

        verify(accountSdk).listAccounts(userId)
    }

    @Test
    fun `test get account by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, appConfig)

        val userId = randomUUID()
        val accountId = randomUUID()

        val externalAccount = ExternalAccountTO(
            accountId,
            ExternalAccountName("Investment Account"),
            Currency.USD
        )

        whenever(accountSdk.findAccountById(userId, accountId)).thenReturn(externalAccount)

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts/$accountId") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val account = response.body<AccountTO>()
        assertThat(account).isNotNull
        assertThat(account.name).isEqualTo(AccountName("Investment Account"))
        assertThat(account.unit).isEqualTo(Currency.USD)
        assertThat(account.id).isEqualTo(accountId)

        verify(accountSdk).findAccountById(userId, accountId)
    }

    @Test
    fun `test get account by id not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, appConfig)

        val userId = randomUUID()
        val accountId = randomUUID()

        whenever(accountSdk.findAccountById(userId, accountId)).thenReturn(null)

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts/$accountId") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)

        verify(accountSdk).findAccountById(userId, accountId)
    }

    @Test
    fun `test create account`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, appConfig)

        val userId = randomUUID()
        val accountId = randomUUID()

        val createRequest = CreateAccountTO(
            name = AccountName("New Checking Account"),
            unit = Currency.RON
        )

        val externalCreateRequest = ExternalCreateAccountTO(
            name = ExternalAccountName("New Checking Account"),
            unit = Currency.RON
        )

        val externalAccount = ExternalAccountTO(
            accountId,
            ExternalAccountName("New Checking Account"),
            Currency.RON
        )

        whenever(accountSdk.createAccount(userId, externalCreateRequest)).thenReturn(externalAccount)

        val response = createJsonHttpClient().post("/funds-api/fund/v1/accounts") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(createRequest)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val account = response.body<AccountTO>()
        assertThat(account).isNotNull
        assertThat(account.name).isEqualTo(AccountName("New Checking Account"))
        assertThat(account.unit).isEqualTo(Currency.RON)
        assertThat(account.id).isEqualTo(accountId)

        verify(accountSdk).createAccount(userId, externalCreateRequest)
    }

    @Test
    fun `test delete account by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, appConfig)

        val userId = randomUUID()
        val accountId = randomUUID()

        val response = createJsonHttpClient().delete("/funds-api/fund/v1/accounts/$accountId") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)

        verify(accountSdk).deleteAccountById(userId, accountId)
    }

    private val appConfig = MapApplicationConfig(
        "integration.account-service.base-url" to MockServerContainerExtension.baseUrl
    )

    private fun Application.testModule() {
        val accountAppTestModule = module {
            single<AccountSdk> { accountSdk }
        }
        configureDependencies(fundDependencies, accountAppTestModule)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }
}