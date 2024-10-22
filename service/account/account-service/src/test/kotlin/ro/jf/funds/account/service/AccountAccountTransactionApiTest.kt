package ro.jf.funds.account.service

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironmentWithDB
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class AccountAccountTransactionApiTest {
    private val database by lazy {
        Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    }

    private val transactionRepository = AccountTransactionRepository(database)
    private val accountRepository = AccountRepository(database)

    @AfterEach
    fun tearDown() = runBlocking {
        transactionRepository.deleteAll()
    }

    @Test
    fun `test create transaction`(): Unit = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val account1 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("Revolut"), "RON"))
        val account2 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("BT"), "RON"))
        val createTransactionRequest = CreateAccountTransactionTO(
            dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
            records = listOf(
                CreateAccountRecordTO(
                    accountId = account1.id,
                    amount = BigDecimal("123.45"),
                    metadata = mapOf("externalId" to "record1")
                ),
                CreateAccountRecordTO(
                    accountId = account2.id,
                    amount = BigDecimal("-123.45"),
                    metadata = mapOf("externalId" to "record2")
                )
            ),
            metadata = mapOf("externalId" to "transaction1")
        )

        val response = createJsonHttpClient()
            .post("/bk-api/account/v1/transactions") {
                header(USER_ID_HEADER, userId)
                contentType(ContentType.Application.Json)
                setBody(createTransactionRequest)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val transaction = response.body<AccountTransactionTO>()
        assertThat(transaction).isNotNull
        assertThat(transaction.dateTime).isEqualTo(createTransactionRequest.dateTime)
        assertThat(transaction.metadata["externalId"]).isEqualTo("transaction1")
        assertThat(transaction.records).hasSize(2)
        assertThat(transaction.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction.records[0].amount).isEqualTo(BigDecimal("123.45"))
        assertThat(transaction.records[0].metadata["externalId"]).isEqualTo("record1")
        assertThat(transaction.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction.records[1].amount).isEqualTo(BigDecimal("-123.45"))
        assertThat(transaction.records[1].metadata["externalId"]).isEqualTo("record2")
    }

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("Revolut"), "RON"))
        val account2 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("BT"), "RON"))
        transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        metadata = mapOf("externalId" to "record1")
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        metadata = mapOf("externalId" to "record2")
                    )
                ),
                metadata = mapOf("externalId" to "transaction1")
            )
        )
        transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(50.123),
                        metadata = mapOf("externalId" to "record3")
                    ),
                ),
                metadata = mapOf("externalId" to "transaction2")
            )
        )

        val response = createJsonHttpClient().get("/bk-api/account/v1/transactions") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ro.jf.funds.commons.model.ListTO<AccountTransactionTO>>()
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

    @Test
    fun `test delete transaction`() = testApplication {
        configureEnvironmentWithDB { module() }

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("Revolut"), "RON"))
        val account2 = accountRepository.save(userId, CreateCurrencyAccountTO(AccountName("BT"), "RON"))
        val transaction = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        metadata = mapOf("externalId" to "record1")
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        metadata = mapOf("externalId" to "record2")
                    )
                ),
                metadata = mapOf("externalId" to "transaction1")
            )
        )

        val response = createJsonHttpClient()
            .delete("/bk-api/account/v1/transactions/${transaction.id}") {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(transactionRepository.findById(userId, transaction.id)).isNull()
    }
}
