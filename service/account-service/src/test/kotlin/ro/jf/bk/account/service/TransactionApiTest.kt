package ro.jf.bk.account.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.account.service.adapter.persistence.AccountExposedRepository
import ro.jf.bk.account.service.adapter.persistence.TransactionExposedRepository
import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateRecordCommand
import ro.jf.bk.account.service.domain.command.CreateTransactionCommand
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.test.extension.PostgresContainerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class TransactionApiTest {
    private val database by lazy {
        Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    }

    private val transactionRepository = TransactionExposedRepository(database)
    private val accountRepository = AccountExposedRepository(database)

    @AfterEach
    fun tearDown() = runBlocking {
        transactionRepository.deleteAll()
    }

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironment()

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(CreateCurrencyAccountCommand(userId, "Revolut", "RON"))
        val account2 = accountRepository.save(CreateCurrencyAccountCommand(userId, "BT", "RON"))
        transactionRepository.save(
            CreateTransactionCommand(
                userId = userId,
                dateTime = dateTime,
                records = listOf(
                    CreateRecordCommand(accountId = account1.id, amount = BigDecimal(100.0), metadata = mapOf("externalId" to "record1")),
                    CreateRecordCommand(accountId = account2.id, amount = BigDecimal(-100.0), metadata = mapOf("externalId" to "record2"))
                ),
                metadata = mapOf("externalId" to "transaction1")
            )
        )
        transactionRepository.save(
            CreateTransactionCommand(
                userId = userId,
                dateTime = dateTime,
                records = listOf(
                    CreateRecordCommand(accountId = account1.id, amount = BigDecimal(50.123), metadata = mapOf("externalId" to "record3")),
                ),
                metadata = mapOf("externalId" to "transaction2")
            )
        )

        val response = createJsonHttpClient().get("/bk-api/account/v1/transactions") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<TransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val transaction1 = transactions.items.first { it.metadata["externalId"] == "transaction1" }
        assertThat(transaction1.records).hasSize(2)
        assertThat(transaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(transaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction1.records[0].metadata["externalId"]).isEqualTo("record1")
        assertThat(transaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(transaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction1.records[1].metadata["externalId"]).isEqualTo("record2")
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    // TODO(Johann) could extract
    private fun ApplicationTestBuilder.configureEnvironment() {
        environment {
            config = MapApplicationConfig(
                "database.url" to PostgresContainerExtension.jdbcUrl,
                "database.user" to PostgresContainerExtension.username,
                "database.password" to PostgresContainerExtension.password
            )
        }
        application {
            module()
        }
    }
}
