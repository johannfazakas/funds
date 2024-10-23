package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountRecordTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.service.config.configureContentNegotiation
import ro.jf.funds.commons.service.config.configureDatabaseMigration
import ro.jf.funds.commons.service.config.configureDependencies
import ro.jf.funds.commons.test.extension.MockServerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironmentWithDB
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.service.config.configureRouting
import ro.jf.funds.fund.service.config.fundsAppModule
import java.math.BigDecimal
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(MockServerExtension::class)
class FundTransactionApiTest {
    private val accountTransactionSdk: AccountTransactionSdk = mock()
    private val accountSdk: AccountSdk = mock()

    @Test
    fun `test create transaction`(): Unit = testApplication {
        configureEnvironmentWithDB { testModule() }
        val userId = randomUUID()
        val companyAccountId = randomUUID()
        val personalAccountId = randomUUID()
        val workFundId = randomUUID()
        val expensesFundId = randomUUID()
        val transactionTime = LocalDateTime.parse("2021-09-01T12:00:00")
        whenever(
            accountTransactionSdk.createTransaction(
                userId, CreateAccountTransactionTO(
                    dateTime = transactionTime,
                    records = listOf(
                        CreateAccountRecordTO(
                            accountId = companyAccountId,
                            amount = BigDecimal("-100.25"),
                            unit = Currency.RON,
                            metadata = mapOf("fundId" to workFundId.toString())
                        ),
                        CreateAccountRecordTO(
                            accountId = personalAccountId,
                            amount = BigDecimal("100.25"),
                            unit = Currency.RON,
                            metadata = mapOf("fundId" to expensesFundId.toString())
                        )
                    ),
                    metadata = emptyMap()
                )
            )
        ).thenReturn(
            AccountTransactionTO(
                id = randomUUID(),
                dateTime = transactionTime,
                records = listOf(
                    AccountRecordTO(
                        id = randomUUID(),
                        accountId = companyAccountId,
                        amount = BigDecimal("-100.25"),
                        unit = Currency.RON,
                        metadata = mapOf("fundId" to workFundId.toString())
                    ),
                    AccountRecordTO(
                        id = randomUUID(),
                        accountId = personalAccountId,
                        amount = BigDecimal("100.25"),
                        unit = Currency.RON,
                        metadata = mapOf("fundId" to expensesFundId.toString())
                    )
                ),
                metadata = emptyMap()
            )
        )

        val response = createJsonHttpClient()
            .post("/bk-api/fund/v1/transactions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId.toString())
                setBody(
                    CreateFundTransactionTO(
                        dateTime = transactionTime,
                        records = listOf(
                            CreateFundRecordTO(
                                fundId = workFundId,
                                accountId = companyAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("-100.25"),
                            ),
                            CreateFundRecordTO(
                                fundId = expensesFundId,
                                accountId = personalAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("100.25"),
                            )
                        )
                    )
                )
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)

        val transaction = response.body<FundTransactionTO>()
        assertThat(transaction.id).isNotNull()
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.records).hasSize(2)
        val record1 = transaction.records[0]
        assertThat(record1.id).isNotNull()
        assertThat(record1.fundId).isEqualTo(workFundId)
        assertThat(record1.accountId).isEqualTo(companyAccountId)
        assertThat(record1.amount).isEqualTo(BigDecimal("-100.25"))
        val record2 = transaction.records[1]
        assertThat(record2.fundId).isEqualTo(expensesFundId)
        assertThat(record2.id).isNotNull()
        assertThat(record2.accountId).isEqualTo(personalAccountId)
        assertThat(record2.amount).isEqualTo(BigDecimal("100.25"))
    }

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironmentWithDB { testModule() }

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

        whenever(accountTransactionSdk.listTransactions(userId)).thenReturn(
            ListTO(
                listOf(
                    AccountTransactionTO(
                        id = transactionId,
                        dateTime = transactionTime,
                        records = listOf(
                            AccountRecordTO(
                                id = record1Id,
                                accountId = account1Id,
                                amount = BigDecimal(100.25),
                                unit = Currency.RON,
                                metadata = mapOf("fundId" to fund1Id.toString()),
                            ),
                            AccountRecordTO(
                                id = record2Id,
                                accountId = account2Id,
                                amount = BigDecimal(50.75),
                                unit = Currency.RON,
                                metadata = mapOf("fundId" to fund2Id.toString()),
                            )
                        ),
                        metadata = emptyMap()
                    )
                )
            )
        )

        val response = createJsonHttpClient()
            .get("/bk-api/fund/v1/transactions") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<FundTransactionTO>>()
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
        configureEnvironmentWithDB { testModule() }

        val userId = randomUUID()
        val transactionId = randomUUID()

        whenever(accountTransactionSdk.deleteTransaction(userId, transactionId)).thenReturn(Unit)

        val response = createJsonHttpClient()
            .delete("/bk-api/fund/v1/transactions/$transactionId") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        verify(accountTransactionSdk).deleteTransaction(userId, transactionId)
    }

    private fun Application.testModule() {
        val fundsAppTestModule = module {
            single<AccountSdk> { accountSdk }
            single<AccountTransactionSdk> { accountTransactionSdk }
        }
        configureDependencies(fundsAppModule, fundsAppTestModule)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureRouting()
    }
}
