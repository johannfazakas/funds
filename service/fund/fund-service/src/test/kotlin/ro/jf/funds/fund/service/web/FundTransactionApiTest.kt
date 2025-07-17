package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.config.configureFundErrorHandling
import ro.jf.funds.fund.service.config.configureFundRouting
import ro.jf.funds.fund.service.config.fundDependencies
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.service.FUND_ID_PROPERTY
import java.math.BigDecimal
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(MockServerContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class FundTransactionApiTest {

    private val fundRepository = FundRepository(PostgresContainerExtension.connection)

    private val accountTransactionSdk: AccountTransactionSdk = mock()
    private val accountSdk: AccountSdk = mock()

    private val userId = randomUUID()

    private val transaction1Id = randomUUID()
    private val transactionExternalId = randomUUID().toString()

    private val record1Id = randomUUID()
    private val record2Id = randomUUID()

    private val companyAccountId = randomUUID()
    private val personalAccountId = randomUUID()

    @Test
    fun `test create transaction`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val transactionTime = LocalDateTime.parse("2021-09-01T12:00:00")
        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        whenever(
            accountTransactionSdk.createTransaction(
                userId, CreateAccountTransactionTO(
                    dateTime = transactionTime,
                    externalId = transactionExternalId,
                    records = listOf(
                        CreateAccountRecordTO(
                            accountId = companyAccountId,
                            amount = BigDecimal("-100.25"),
                            unit = Currency.RON,
                            labels = listOf(Label("one"), Label("two")),
                            properties = propertiesOf("fundId" to workFund.id.toString())
                        ),
                        CreateAccountRecordTO(
                            accountId = personalAccountId,
                            amount = BigDecimal("100.25"),
                            unit = Currency.RON,
                            properties = propertiesOf("fundId" to expensesFund.id.toString())
                        )
                    ),
                    properties = propertiesOf()
                )
            )
        ).thenReturn(
            AccountTransactionTO(
                id = randomUUID(),
                dateTime = transactionTime,
                externalId = transactionExternalId,
                records = listOf(
                    AccountRecordTO(
                        id = randomUUID(),
                        accountId = companyAccountId,
                        amount = BigDecimal("-100.25"),
                        unit = Currency.RON,
                        labels = listOf(Label("one"), Label("two")),
                        properties = propertiesOf("fundId" to workFund.id.toString())
                    ),
                    AccountRecordTO(
                        id = randomUUID(),
                        accountId = personalAccountId,
                        amount = BigDecimal("100.25"),
                        unit = Currency.RON,
                        labels = emptyList(),
                        properties = propertiesOf("fundId" to expensesFund.id.toString())
                    )
                ),
                properties = propertiesOf()
            )
        )

        val response = createJsonHttpClient()
            .post("/funds-api/fund/v1/transactions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId.toString())
                setBody(
                    CreateFundTransactionTO(
                        dateTime = transactionTime,
                        externalId = transactionExternalId,
                        records = listOf(
                            CreateFundRecordTO(
                                fundId = workFund.id,
                                accountId = companyAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("-100.25"),
                                labels = listOf(Label("one"), Label("two")),
                            ),
                            CreateFundRecordTO(
                                fundId = expensesFund.id,
                                accountId = personalAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("100.25"),
                                labels = emptyList()
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
        assertThat(record1.fundId).isEqualTo(workFund.id)
        assertThat(record1.accountId).isEqualTo(companyAccountId)
        assertThat(record1.amount).isEqualTo(BigDecimal("-100.25"))
        assertThat(record1.labels).containsExactly(Label("one"), Label("two"))
        val record2 = transaction.records[1]
        assertThat(record2.fundId).isEqualTo(expensesFund.id)
        assertThat(record2.id).isNotNull()
        assertThat(record2.accountId).isEqualTo(personalAccountId)
        assertThat(record2.amount).isEqualTo(BigDecimal("100.25"))
        assertThat(record2.labels).isEmpty()
    }

    @Test
    fun `test create transaction when fund not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val transactionTime = LocalDateTime.parse("2021-09-01T12:00:00")
        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFundId = randomUUID()

        val response = createJsonHttpClient()
            .post("/funds-api/fund/v1/transactions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId.toString())
                setBody(
                    CreateFundTransactionTO(
                        dateTime = transactionTime,
                        externalId = transactionExternalId,
                        records = listOf(
                            CreateFundRecordTO(
                                fundId = workFund.id,
                                accountId = companyAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("-100.25"),
                                labels = listOf(Label("one"), Label("two")),
                            ),
                            CreateFundRecordTO(
                                fundId = expensesFundId,
                                accountId = personalAccountId,
                                unit = Currency.RON,
                                amount = BigDecimal("100.25"),
                                labels = emptyList()
                            )
                        )
                    )
                )
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)

        val error = response.body<ErrorTO>()
        assertThat(error.title).isEqualTo("Transaction fund not found")
        assertThat(error.detail).contains(expensesFundId.toString())
    }

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val rawTransactionTime = "2021-09-01T12:00:00"
        val transactionTime = LocalDateTime.parse(rawTransactionTime)
        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        whenever(accountTransactionSdk.listTransactions(userId, AccountTransactionFilterTO.empty())).thenReturn(
            ListTO(
                listOf(
                    AccountTransactionTO(
                        id = transaction1Id,
                        dateTime = transactionTime,
                        externalId = transactionExternalId,
                        records = listOf(
                            AccountRecordTO(
                                id = record1Id,
                                accountId = companyAccountId,
                                amount = BigDecimal(100.25),
                                unit = Currency.RON,
                                labels = listOf(Label("one"), Label("two")),
                                properties = propertiesOf("fundId" to workFund.id.toString()),
                            ),
                            AccountRecordTO(
                                id = record2Id,
                                accountId = personalAccountId,
                                amount = BigDecimal(50.75),
                                unit = Currency.RON,
                                labels = emptyList(),
                                properties = propertiesOf("fundId" to expensesFund.id.toString()),
                            )
                        ),
                        properties = propertiesOf()
                    )
                )
            )
        )

        val response = createJsonHttpClient()
            .get("/funds-api/fund/v1/transactions") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<FundTransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transaction1Id)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.records).hasSize(2)
        val record1 = transaction.records[0]
        assertThat(record1.id).isEqualTo(record1Id)
        assertThat(record1.accountId).isEqualTo(companyAccountId)
        assertThat(record1.amount).isEqualTo(BigDecimal(100.25))
        assertThat(record1.fundId).isEqualTo(workFund.id)
        assertThat(record1.labels).containsExactly(Label("one"), Label("two"))
        val record2 = transaction.records[1]
        assertThat(record2.id).isEqualTo(record2Id)
        assertThat(record2.accountId).isEqualTo(personalAccountId)
        assertThat(record2.amount).isEqualTo(BigDecimal(50.75))
        assertThat(record2.fundId).isEqualTo(expensesFund.id)
        assertThat(record2.labels).isEmpty()
    }

    @Test
    fun `test list transactions filtered by date`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val fromDate = LocalDate(2021, 9, 1)
        val toDate = LocalDate(2021, 9, 30)
        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))
        val filter = AccountTransactionFilterTO(fromDate = fromDate, toDate = toDate)

        whenever(accountTransactionSdk.listTransactions(userId, filter)).thenReturn(
            ListTO(
                listOf(
                    AccountTransactionTO(
                        id = transaction1Id,
                        dateTime = fromDate.atTime(12, 45),
                        externalId = transactionExternalId,
                        records = listOf(
                            AccountRecordTO(
                                id = record1Id,
                                accountId = companyAccountId,
                                amount = BigDecimal(100.25),
                                unit = Currency.RON,
                                labels = listOf(Label("one"), Label("two")),
                                properties = propertiesOf("fundId" to workFund.id.toString()),
                            ),
                            AccountRecordTO(
                                id = record2Id,
                                accountId = personalAccountId,
                                amount = BigDecimal(50.75),
                                unit = Currency.RON,
                                labels = emptyList(),
                                properties = propertiesOf("fundId" to expensesFund.id.toString()),
                            )
                        ),
                        properties = propertiesOf()
                    )
                )
            )
        )

        val response = createJsonHttpClient()
            .get("/funds-api/fund/v1/transactions") {
                parameter("fromDate", fromDate.toString())
                parameter("toDate", toDate.toString())
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<FundTransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        verify(accountTransactionSdk).listTransactions(userId, AccountTransactionFilterTO(fromDate = fromDate, toDate = toDate))
    }

    @Test
    fun `test list fund transactions`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val fundId = randomUUID()
        val rawTransactionTime = "2021-09-01T12:00:00"
        val transactionTime = LocalDateTime.parse(rawTransactionTime)

        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        val filter = AccountTransactionFilterTO(
            recordProperties = listOf(PropertyTO(FUND_ID_PROPERTY, fundId.toString()))
        )
        whenever(accountTransactionSdk.listTransactions(userId, filter)).thenReturn(
            ListTO(
                listOf(
                    AccountTransactionTO(
                        id = transaction1Id,
                        externalId = transactionExternalId,
                        dateTime = transactionTime,
                        records = listOf(
                            AccountRecordTO(
                                id = record1Id,
                                accountId = companyAccountId,
                                amount = BigDecimal(100.25),
                                unit = Currency.RON,
                                labels = listOf(Label("one"), Label("two")),
                                properties = propertiesOf("fundId" to workFund.id.toString()),
                            ),
                            AccountRecordTO(
                                id = record2Id,
                                accountId = personalAccountId,
                                amount = BigDecimal(50.75),
                                unit = Currency.RON,
                                labels = emptyList(),
                                properties = propertiesOf("fundId" to expensesFund.id.toString()),
                            )
                        ),
                        properties = propertiesOf()
                    )
                )
            )
        )

        val response = createJsonHttpClient()
            .get("/funds-api/fund/v1/funds/$fundId/transactions") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<FundTransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        val transaction = transactions.items.first()
        assertThat(transaction.id).isEqualTo(transaction1Id)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.records).hasSize(2)
        val record1 = transaction.records[0]
        assertThat(record1.id).isEqualTo(record1Id)
        assertThat(record1.accountId).isEqualTo(companyAccountId)
        assertThat(record1.amount).isEqualTo(BigDecimal(100.25))
        assertThat(record1.fundId).isEqualTo(workFund.id)
        assertThat(record1.labels).containsExactly(Label("one"), Label("two"))
        val record2 = transaction.records[1]
        assertThat(record2.id).isEqualTo(record2Id)
        assertThat(record2.accountId).isEqualTo(personalAccountId)
        assertThat(record2.amount).isEqualTo(BigDecimal(50.75))
        assertThat(record2.fundId).isEqualTo(expensesFund.id)
        assertThat(record2.labels).isEmpty()
    }

    @Test
    fun `test remove transaction`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        whenever(accountTransactionSdk.deleteTransaction(userId, transaction1Id)).thenReturn(Unit)

        val response = createJsonHttpClient()
            .delete("/funds-api/fund/v1/transactions/$transaction1Id") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        verify(accountTransactionSdk).deleteTransaction(userId, transaction1Id)
    }

    private fun Application.testModule() {
        val fundsAppTestModule = module {
            single<AccountSdk> { accountSdk }
            single<AccountTransactionSdk> { accountTransactionSdk }
        }
        configureDependencies(fundDependencies, fundsAppTestModule)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }
}
