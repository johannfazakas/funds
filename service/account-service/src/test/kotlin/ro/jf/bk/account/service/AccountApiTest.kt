package ro.jf.bk.account.service

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.account.service.adapter.persistence.AccountExposedRepository
import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.test.extension.PostgresContainerExtension
import ro.jf.bk.commons.test.utils.configureEnvironmentWithDB
import ro.jf.bk.commons.test.utils.createJsonHttpClient
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class AccountApiTest {

    private val accountRepository = createAccountRepository()

    @AfterEach
    fun tearDown() = runBlocking {
        accountRepository.deleteAll()
    }

    @Test
    fun `test list accounts`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        accountRepository.save(CreateCurrencyAccountCommand(userId, AccountName("Cash"), "RON"))
        accountRepository.save(CreateInstrumentAccountCommand(userId, AccountName("BET"), "RON", "TVBETETF"))

        val response = createJsonHttpClient().get("/bk-api/account/v1/accounts") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val accounts = response.body<ListTO<AccountTO>>()
        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items.map { it.name }).containsExactlyInAnyOrder(AccountName("Cash"), AccountName("BET"))
    }

    @Test
    fun `test get account by id`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val user = accountRepository.save(CreateCurrencyAccountCommand(userId, AccountName("Revolut"), "RON"))

        val response = createJsonHttpClient().get("/bk-api/account/v1/accounts/${user.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val accountTO = response.body<AccountTO>()
        assertThat(accountTO).isNotNull
        assertThat(accountTO.name).isEqualTo(AccountName("Revolut"))
    }

    @Test
    fun `test get account by id when missing`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val response = createJsonHttpClient().get("/bk-api/account/v1/accounts/${randomUUID()}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `test create currency account`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts/currency") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateCurrencyAccountTO(AccountName("Revolut"), "RON"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val accountTO = response.body<AccountTO.Currency>()
        assertThat(accountTO).isNotNull
        assertThat(accountTO.name).isEqualTo(AccountName("Revolut"))

        val dbAccount = accountRepository.findById(userId, accountTO.id)
        assertThat(dbAccount).isNotNull
        assertThat(dbAccount!!.name).isEqualTo(AccountName("Revolut"))
    }

    @Test
    fun `test create instrument account`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts/instrument") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateInstrumentAccountTO(AccountName("S&P500"), "EUR", "SXR8_DE"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val accountTO = response.body<AccountTO.Instrument>()
        assertThat(accountTO).isNotNull
        assertThat(accountTO.name).isEqualTo(AccountName("S&P500"))
        assertThat(accountTO.symbol).isEqualTo("SXR8_DE")
        assertThat(accountTO.currency).isEqualTo("EUR")

        val dbAccount = accountRepository.findById(userId, accountTO.id)
                as? Account.Instrument ?: error("Account is not an instrument")
        assertThat(dbAccount).isNotNull
        assertThat(dbAccount.name).isEqualTo(AccountName("S&P500"))
        assertThat(dbAccount.currency).isEqualTo("EUR")
        assertThat(dbAccount.symbol).isEqualTo("SXR8_DE")
    }

    @Test
    fun `test create account with duplicate name`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        accountRepository.save(CreateCurrencyAccountCommand(userId, AccountName("BT"), "EUR"))

        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts/currency") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateCurrencyAccountTO(AccountName("BT"), "RON"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun `test delete account by id`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val account = accountRepository.save(CreateCurrencyAccountCommand(userId, AccountName("ING"), "RON"))

        val response = createJsonHttpClient().delete("/bk-api/account/v1/accounts/${account.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(accountRepository.findById(userId, account.id)).isNull()
    }

    @Test
    fun `test delete not existing user by id`() = testApplication {
        configureEnvironmentWithDB { module() }

        val response = createJsonHttpClient().delete("/bk-api/account/v1/accounts/${randomUUID()}") {
            header(USER_ID_HEADER, randomUUID())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
    }

    private fun createAccountRepository() = AccountExposedRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}
