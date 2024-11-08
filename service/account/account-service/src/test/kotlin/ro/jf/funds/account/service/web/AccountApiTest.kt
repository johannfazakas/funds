package ro.jf.funds.account.service.web

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
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.account.service.module
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironmentWithDB
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class AccountApiTest {

    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)

    @AfterEach
    fun tearDown() = runBlocking {
        accountRepository.deleteAll()
    }

    @Test
    fun `test list accounts`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("Cash"), Currency.RON))
        accountRepository.save(userId, CreateAccountTO(AccountName("BET"), Symbol("TVBETETF")))

        val response = createJsonHttpClient().get("/bk-api/account/v1/accounts") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val accounts = response.body<ro.jf.funds.commons.model.ListTO<AccountTO>>()
        assertThat(accounts.items).hasSize(2)
        assertThat(accounts.items.map { it.name }).containsExactlyInAnyOrder(AccountName("Cash"), AccountName("BET"))
    }

    @Test
    fun `test get account by id`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val user = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))

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
        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateAccountTO(AccountName("Revolut"), Currency.RON))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val accountTO = response.body<AccountTO>()
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
        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateAccountTO(AccountName("S&P500"), Symbol("SXR8_DE")))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val accountTO = response.body<AccountTO>()
        assertThat(accountTO).isNotNull
        assertThat(accountTO.name).isEqualTo(AccountName("S&P500"))
        assertThat(accountTO.unit).isEqualTo(Symbol("SXR8_DE"))

        val dbAccount = accountRepository.findById(userId, accountTO.id)
        assertThat(dbAccount).isNotNull
        assertThat(dbAccount?.name).isEqualTo(AccountName("S&P500"))
        assertThat(dbAccount?.unit).isEqualTo(Symbol("SXR8_DE"))
    }

    @Test
    fun `test create account with duplicate name`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.EUR))

        val response = createJsonHttpClient().post("/bk-api/account/v1/accounts") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateAccountTO(AccountName("BT"), Currency.RON))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun `test delete account by id`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("ING"), Currency.RON))

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
}
