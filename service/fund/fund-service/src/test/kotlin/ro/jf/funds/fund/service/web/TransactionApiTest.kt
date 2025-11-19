package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import ro.jf.funds.commons.api.model.Currency
import ro.jf.funds.commons.api.model.Label
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
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
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import java.math.BigDecimal
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class TransactionApiTest {
    private val database = PostgresContainerExtension.connection
    private val fundRepository = FundRepository(database)
    private val accountRepository = AccountRepository(database)
    private val transactionRepository = TransactionRepository(database)

    private val userId = randomUUID()

    @Test
    fun `test create fund transaction`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val companyAccount = accountRepository.save(userId, CreateAccountTO(AccountName("Company Account"), Currency.RON))
        val personalAccount = accountRepository.save(userId, CreateAccountTO(AccountName("Personal Account"), Currency.RON))

        val transactionTime = LocalDateTime.parse("2021-09-01T12:00:00")
        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        val request: CreateTransactionTO = CreateTransactionTO.Transfer(
            dateTime = transactionTime,
            externalId = randomUUID().toString(),
            sourceRecord = CreateTransactionRecordTO(
                accountId = companyAccount.id,
                fundId = workFund.id,
                amount = BigDecimal("-100.25").toDouble(),
                unit = Currency.RON,
                labels = listOf(Label("one"), Label("two"))
            ),
            destinationRecord = CreateTransactionRecordTO(
                accountId = personalAccount.id,
                fundId = expensesFund.id,
                amount = BigDecimal("100.25").toDouble(),
                unit = Currency.RON
            )
        )

        val response = createJsonHttpClient().post("/funds-api/fund/v1/transactions") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(request)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val fundTransaction = response.body<TransactionTO>()
        assertThat(fundTransaction).isNotNull
        assertThat(fundTransaction).isInstanceOf(TransactionTO.Transfer::class.java)
        val transferTransaction = fundTransaction as TransactionTO.Transfer
        assertThat(transferTransaction.sourceRecord).isNotNull
        assertThat(transferTransaction.destinationRecord).isNotNull
    }

    @Test
    fun `test list fund transactions with no filter`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))

        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        transactionRepository.save(
            userId,
            CreateTransactionTO.Transfer(
                dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
                externalId = "transaction1",
                sourceRecord = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("-100.25").toDouble(),
                    unit = Currency.RON,
                    labels = listOf(Label("salary"))
                ),
                destinationRecord = CreateTransactionRecordTO(
                    accountId = account2.id,
                    fundId = expensesFund.id,
                    amount = BigDecimal("100.25").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = LocalDateTime.parse("2021-09-02T15:30:00"),
                externalId = "transaction2",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("50.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        val response = createJsonHttpClient().get("/funds-api/fund/v1/transactions") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val transactions = response.body<ListTO<TransactionTO>>()
        assertThat(transactions.items).hasSize(2)

        val fundTransaction1 = transactions.items.find { it.type == TransactionType.TRANSFER } as? TransactionTO.Transfer
        assertThat(fundTransaction1).isNotNull
        assertThat(fundTransaction1!!.sourceRecord.amount).isEqualByComparingTo(BigDecimal("-100.25").toDouble())
        assertThat(fundTransaction1.sourceRecord.fundId).isEqualTo(workFund.id)
        assertThat(fundTransaction1.sourceRecord.accountId).isEqualTo(account1.id)
        assertThat(fundTransaction1.sourceRecord.labels).contains(Label("salary"))
        assertThat(fundTransaction1.destinationRecord.amount).isEqualByComparingTo(BigDecimal("100.25").toDouble())
        assertThat(fundTransaction1.destinationRecord.fundId).isEqualTo(expensesFund.id)
        assertThat(fundTransaction1.destinationRecord.accountId).isEqualTo(account2.id)

        val fundTransaction2 = transactions.items.find { it.type == TransactionType.SINGLE_RECORD } as? TransactionTO.SingleRecord
        assertThat(fundTransaction2).isNotNull
        assertThat(fundTransaction2!!.record.amount).isEqualByComparingTo(BigDecimal("50.00").toDouble())
        assertThat(fundTransaction2.record.fundId).isEqualTo(workFund.id)
        assertThat(fundTransaction2.record.accountId).isEqualTo(account1.id)
    }

    @Test
    fun `test list fund transactions with date filter`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))

        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))

        val fromDate = LocalDate.parse("2021-07-22")
        val toDate = LocalDate.parse("2021-07-24")

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = fromDate.minus(1, DateTimeUnit.DAY).atTime(12, 0),
                externalId = "before-range",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("100.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = fromDate.atTime(15, 30),
                externalId = "in-range",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("200.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = toDate.plus(1, DateTimeUnit.DAY).atTime(12, 0),
                externalId = "after-range",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("300.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        val response = createJsonHttpClient().get("/funds-api/fund/v1/transactions") {
            header(USER_ID_HEADER, userId)
            parameter("fromDate", fromDate)
            parameter("toDate", toDate)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val transactions = response.body<ListTO<TransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        val transaction = transactions.items[0] as TransactionTO.SingleRecord
        assertThat(transaction.record.amount).isEqualByComparingTo(BigDecimal("200.00").toDouble())
        assertThat(transaction.dateTime).isEqualTo(fromDate.atTime(15, 30))
    }

    @Test
    fun `test list fund transactions with fund filter`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))

        val workFund = fundRepository.save(userId, CreateFundTO(FundName("Work")))
        val expensesFund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
                externalId = "work-transaction",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = workFund.id,
                    amount = BigDecimal("100.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        transactionRepository.save(
            userId,
            CreateTransactionTO.SingleRecord(
                dateTime = LocalDateTime.parse("2021-09-02T15:30:00"),
                externalId = "expenses-transaction",
                record = CreateTransactionRecordTO(
                    accountId = account1.id,
                    fundId = expensesFund.id,
                    amount = BigDecimal("200.00").toDouble(),
                    unit = Currency.RON
                )
            )
        )

        val response = createJsonHttpClient().get("/funds-api/fund/v1/transactions") {
            header(USER_ID_HEADER, userId)
            parameter("fundId", workFund.id)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val transactions = response.body<ListTO<TransactionTO>>()
        assertThat(transactions.items).hasSize(1)
        val transaction = transactions.items[0] as TransactionTO.SingleRecord
        assertThat(transaction.record.amount).isEqualByComparingTo(BigDecimal("100.00").toDouble())
        assertThat(transaction.record.fundId).isEqualTo(workFund.id)
    }

    private fun Application.testModule() {
        configureDependencies(fundDependencies)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }
}