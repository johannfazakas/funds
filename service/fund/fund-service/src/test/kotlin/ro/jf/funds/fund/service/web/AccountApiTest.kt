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
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.createJsonHttpClient
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.config.configureFundErrorHandling
import ro.jf.funds.fund.service.config.configureFundRouting
import ro.jf.funds.fund.service.config.fundDependencies
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
import java.math.BigDecimal
import java.util.UUID.randomUUID
import com.ionspin.kotlin.bignum.decimal.toBigDecimal as toKBigDecimal
import kotlinx.datetime.LocalDateTime
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountApiTest {
    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)
    private val fundRepository = FundRepository(PostgresContainerExtension.connection)
    private val recordRepository = RecordRepository(PostgresContainerExtension.connection)
    private val transactionRepository = TransactionRepository(PostgresContainerExtension.connection)

    @AfterEach
    fun tearDown() = runBlocking {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `given accounts exist when listing without pagination then returns all accounts`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("Checking Account"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("Savings Account"), Currency.EUR))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val accounts = response.body<PageTO<AccountTO>>()
        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items.map { it.name.value }).containsExactlyInAnyOrder("Checking Account", "Savings Account")
        assertThat(accounts.items.map { it.unit }).containsExactlyInAnyOrder(Currency.RON, Currency.EUR)
        assertThat(accounts.total).isEqualTo(2)
    }

    @Test
    fun `given multiple accounts when listing with offset and limit then returns correct slice`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("Alpha"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("Beta"), Currency.EUR))
        accountRepository.save(userId, CreateAccountTO(AccountName("Gamma"), Currency.USD))
        accountRepository.save(userId, CreateAccountTO(AccountName("Delta"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("Epsilon"), Currency.EUR))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts?offset=1&limit=2&sort=name&order=asc") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val page = response.body<PageTO<AccountTO>>()
        assertThat(page.items).hasSize(2)
        assertThat(page.items.map { it.name.value }).containsExactly("Beta", "Delta")
        assertThat(page.total).isEqualTo(5)
    }

    @Test
    fun `given multiple accounts when sorting by name descending then returns sorted results`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("Alpha"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("Zeta"), Currency.EUR))
        accountRepository.save(userId, CreateAccountTO(AccountName("Beta"), Currency.USD))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts?sort=name&order=desc") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val page = response.body<PageTO<AccountTO>>()
        assertThat(page.items.map { it.name.value }).containsExactly("Zeta", "Beta", "Alpha")
        assertThat(page.total).isEqualTo(3)
    }

    @Test
    fun `given multiple accounts when sorting by unit then returns sorted results`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("USD Account"), Currency.USD))
        accountRepository.save(userId, CreateAccountTO(AccountName("EUR Account"), Currency.EUR))
        accountRepository.save(userId, CreateAccountTO(AccountName("RON Account"), Currency.RON))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/accounts?sort=unit&order=asc") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val page = response.body<PageTO<AccountTO>>()
        assertThat(page.items.map { it.unit }).containsExactly(Currency.EUR, Currency.RON, Currency.USD)
        assertThat(page.total).isEqualTo(3)
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

    @Test
    fun `given account exists when updating name then returns updated account`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("OldName"), Currency.RON))

        val response = createJsonHttpClient().patch("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountTO(name = AccountName("NewName")))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val updated = response.body<AccountTO>()
        assertThat(updated.id).isEqualTo(account.id)
        assertThat(updated.name).isEqualTo(AccountName("NewName"))
        assertThat(updated.unit).isEqualTo(Currency.RON)

        val dbAccount = accountRepository.findById(userId, account.id)
        assertThat(dbAccount!!.name).isEqualTo(AccountName("NewName"))
    }

    @Test
    fun `given account exists when updating unit with no records then returns updated account`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("MyAccount"), Currency.RON))

        val response = createJsonHttpClient().patch("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountTO(unit = Currency.EUR))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val updated = response.body<AccountTO>()
        assertThat(updated.unit).isEqualTo(Currency.EUR)

        val dbAccount = accountRepository.findById(userId, account.id)
        assertThat(dbAccount!!.unit).isEqualTo(Currency.EUR)
    }

    @Test
    fun `given account not found when updating then returns not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        val nonExistentId = randomUUID()

        val response = createJsonHttpClient().patch("/funds-api/fund/v1/accounts/$nonExistentId") {
            header(USER_ID_HEADER, userId)
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountTO(name = AccountName("NewName")))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given account name already exists when updating to that name then returns conflict`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("ExistingName"), Currency.RON))
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("MyAccount"), Currency.EUR))

        val response = createJsonHttpClient().patch("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountTO(name = AccountName("ExistingName")))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
        val error = response.body<ErrorTO>()
        assertThat(error.title).isEqualTo("Account name already exists")
    }

    @Test
    fun `given account has transaction records when updating unit then returns conflict`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        val fund = fundRepository.save(userId, CreateFundTO(FundName("TestFund")))
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("AccountWithRecords"), Currency.RON))

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                externalId = "tx-1",
                dateTime = LocalDateTime(2024, 1, 1, 12, 0),
                record = CreateTransactionRecordTO.CurrencyRecord(
                    accountId = account.id,
                    fundId = fund.id,
                    amount = 100.0.toKBigDecimal(),
                    unit = Currency.RON,
                    labels = emptyList()
                )
            )
        )

        val response = createJsonHttpClient().patch("/funds-api/fund/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountTO(unit = Currency.EUR))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
        val error = response.body<ErrorTO>()
        assertThat(error.title).isEqualTo("Account has records")
    }

    private fun Application.testModule() {
        configureDependencies(fundDependencies)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }
}