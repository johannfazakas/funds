package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.koin.ktor.ext.get
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
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
import ro.jf.funds.fund.service.persistence.AccountRepository
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountApiTest {
    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)

    @AfterEach
    fun tearDown() = runBlocking {
        accountRepository.deleteAll()
    }

    @Test
    fun `test list accounts`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("Checking Account"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("Savings Account"), Currency.EUR))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val accounts = response.body<ListTO<AccountTO>>()
        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items.map { it.name.value }).containsExactlyInAnyOrder("Checking Account", "Savings Account")
        assertThat(accounts.items.map { it.unit }).containsExactlyInAnyOrder(Currency.RON, Currency.EUR)
    }

    @Test
    fun `test get account by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("Investment Account"), Currency.USD))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val accountTO = response.body<AccountTO>()
        assertThat(accountTO).isNotNull
        assertThat(accountTO.name).isEqualTo(AccountName("Investment Account"))
        assertThat(accountTO.unit).isEqualTo(Currency.USD)
        assertThat(accountTO.id).isEqualTo(account.id)
    }

    @Test
    fun `test get account by id not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val accountId = randomUUID()

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts/$accountId") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `test create account`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val createRequest = CreateAccountTO(
            name = AccountName("New Checking Account"),
            unit = Currency.RON
        )

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

        val dbAccount = accountRepository.findById(userId, account.id)
        assertThat(dbAccount).isNotNull
        assertThat(dbAccount!!.name).isEqualTo(AccountName("New Checking Account"))
        assertThat(dbAccount.unit).isEqualTo(Currency.RON)
    }

    @Test
    fun `test delete account by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("To Delete"), Currency.RON))

        val response = createJsonHttpClient().delete("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(accountRepository.findById(userId, account.id)).isNull()
    }

    private fun Application.testModule() {
        configureDependencies(fundDependencies)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }
}