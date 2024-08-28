package ro.jf.bk.fund.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.service.config.configureContentNegotiation
import ro.jf.bk.commons.service.config.configureDatabaseMigration
import ro.jf.bk.commons.service.config.configureDependencies
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.test.extension.PostgresContainerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.TransactionTO
import ro.jf.bk.fund.service.config.configureRouting
import ro.jf.bk.fund.service.config.fundsAppModule
import ro.jf.bk.fund.service.domain.model.Record
import ro.jf.bk.fund.service.domain.model.Transaction
import ro.jf.bk.fund.service.domain.port.AccountRepository
import ro.jf.bk.fund.service.domain.port.TransactionRepository
import java.math.BigDecimal
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(MockServerExtension::class)
class TransactionApiTest {
    private val transactionRepository: TransactionRepository = mock()
    private val accountRepository: AccountRepository = mock()

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironment()

        val userId = randomUUID()
        val transactionId = randomUUID()
        val record1Id = randomUUID()
        val record2Id = randomUUID()
        val account1Id = randomUUID()
        val account2Id = randomUUID()
        val fund1Id = randomUUID()
        val fund2Id = randomUUID()
        val rawTransactionTime = "2021-09-01T12:00:00"
        val transactionTime = LocalDateTime.parse(rawTransactionTime)

        whenever(transactionRepository.listTransactions(userId)).thenReturn(
            listOf(
                Transaction(
                    id = transactionId,
                    userId = userId,
                    dateTime = transactionTime,
                    records = listOf(
                        Record(
                            id = record1Id,
                            accountId = account1Id,
                            amount = BigDecimal(100.25),
                            fundId = fund1Id
                        ),
                        Record(
                            id = record2Id,
                            accountId = account2Id,
                            amount = BigDecimal(50.75),
                            fundId = fund2Id
                        )
                    )
                )
            )
        )

        val response = createJsonHttpClient()
            .get("/bk-api/fund/v1/transactions") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<TransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.records).hasSize(2)
        val record1 = transaction.records[0]
        assertThat(record1.id).isEqualTo(record1Id)
        assertThat(record1.accountId).isEqualTo(account1Id)
        assertThat(record1.amount).isEqualTo(BigDecimal(100.25))
        assertThat(record1.fundId).isEqualTo(fund1Id)
        val record2 = transaction.records[1]
        assertThat(record2.id).isEqualTo(record2Id)
        assertThat(record2.accountId).isEqualTo(account2Id)
        assertThat(record2.amount).isEqualTo(BigDecimal(50.75))
        assertThat(record2.fundId).isEqualTo(fund2Id)
    }

    @Test
    fun `test remove transaction`() = testApplication {
        configureEnvironment()

        val userId = randomUUID()
        val transactionId = randomUUID()

        whenever(transactionRepository.deleteTransaction(userId, transactionId)).thenReturn(Unit)

        val response = createJsonHttpClient()
            .delete("/bk-api/fund/v1/transactions/$transactionId") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        verify(transactionRepository).deleteTransaction(userId, transactionId)
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    // TODO(Johann) should extract this
    private fun ApplicationTestBuilder.configureEnvironment() {
        environment {
            config = MapApplicationConfig(
                "database.url" to PostgresContainerExtension.jdbcUrl,
                "database.user" to PostgresContainerExtension.username,
                "database.password" to PostgresContainerExtension.password,
            )
        }
        application {
            // TODO(Johann) might simplify this further
            val fundsAppTestModule = module {
                single<AccountRepository> { accountRepository }
                single<TransactionRepository> { transactionRepository }
            }
            configureDependencies(fundsAppModule, fundsAppTestModule)
            configureContentNegotiation()
            configureDatabaseMigration(get<DataSource>())
            configureRouting()
        }
    }
}
