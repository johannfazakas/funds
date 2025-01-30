package ro.jf.funds.account.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.service.module
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class AccountTransactionApiTest {
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
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        val createTransactionRequest = CreateAccountTransactionTO(
            dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
            records = listOf(
                CreateAccountRecordTO(
                    accountId = account1.id,
                    amount = BigDecimal("123.45"),
                    unit = Currency.RON,
                    labels = listOf(Label("label1"), Label("label2")),
                    properties = propertiesOf("externalId" to "record1"),
                ),
                CreateAccountRecordTO(
                    accountId = account2.id,
                    amount = BigDecimal("-123.45"),
                    unit = Currency.RON,
                    labels = listOf(Label("label3")),
                    properties = propertiesOf("externalId" to "record2"),
                )
            ),
            properties = propertiesOf("key" to "val1", "key" to "val2")
        )

        val response = createJsonHttpClient()
            .post("/funds-api/account/v1/transactions") {
                header(USER_ID_HEADER, userId)
                contentType(ContentType.Application.Json)
                setBody(createTransactionRequest)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val transaction = response.body<AccountTransactionTO>()
        assertThat(transaction).isNotNull
        assertThat(transaction.dateTime).isEqualTo(createTransactionRequest.dateTime)
        assertThat(transaction.properties.filter { it.key == "key" }.map { it.value }).isEqualTo(listOf("val1", "val2"))
        assertThat(transaction.records).hasSize(2)
        assertThat(transaction.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction.records[0].amount).isEqualTo(BigDecimal("123.45"))
        assertThat(transaction.records[0].labels).containsExactlyInAnyOrder(Label("label1"), Label("label2"))
        assertThat(transaction.records[0].properties.single { it.key == "externalId" }.value).isEqualTo("record1")
        assertThat(transaction.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction.records[1].amount).isEqualTo(BigDecimal("-123.45"))
        assertThat(transaction.records[1].properties.single { it.key == "externalId" }.value).isEqualTo("record2")
        assertThat(transaction.records[1].labels).containsExactlyInAnyOrder(Label("label3"))
    }

    @Test
    fun `test list transactions`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        properties = propertiesOf(
                            "externalId" to "record1", "someKey" to "someValue", "someKey" to "otherValue"
                        ),
                        labels = listOf(Label("label1"), Label("label2"))
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("externalId" to "record2")
                    )
                ),
                properties = propertiesOf(
                    "externalId" to "transaction1", "transactionProp" to "val1", "transactionProp" to "val2"
                )
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
                        unit = Currency.RON,
                        properties = propertiesOf("externalId" to "record3")
                    ),
                ),
                properties = propertiesOf("externalId" to "transaction2")
            )
        )

        val response = createJsonHttpClient().get("/funds-api/account/v1/transactions") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val transaction1 = transactions.items
            .first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction1") } }
        assertThat(transaction1.records).hasSize(2)
        assertThat(transaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(transaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction1.records[0].labels).containsExactlyInAnyOrder(Label("label1"), Label("label2"))
        assertThat(transaction1.records[0].properties.single { it.key == "externalId" }.value).isEqualTo("record1")
        assertThat(transaction1.records[0].properties.filter { it.key == "someKey" }.map { it.value })
            .containsExactlyInAnyOrder("someValue", "otherValue")
        assertThat(transaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(transaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction1.records[1].labels).isEmpty()
        assertThat(transaction1.records[1].properties.single { it.key == "externalId" }.value).isEqualTo("record2")
        assertThat(transaction1.properties.filter { it.key == "transactionProp" }.map { it.value })
            .containsExactlyInAnyOrder("val1", "val2")

        val transaction2 =
            transactions.items.first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction2") } }
        assertThat(transaction2.records).hasSize(1)
    }

    @Test
    fun `test get transactions by record property`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        labels = listOf(Label("label1")),
                        properties = propertiesOf("some-key" to "one", "other-key" to "x", "other-key" to "y")
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "two", "other-key" to "z")
                    )
                ),
                properties = propertiesOf(
                    "externalId" to "transaction1", "transactionProp" to "val1", "transactionProp" to "val2"
                )
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
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "one", "other-key" to "x")
                    ),
                ),
                properties = propertiesOf(
                    "externalId" to "transaction2", "transactionProp" to "val3"
                )
            )
        )
        transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(12.5),
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "two", "other-key" to "x")
                    ),
                ),
                properties = propertiesOf(
                    "externalId" to "transaction3",
                    "transactionProp" to "val4"
                )
            )
        )

        val response =
            createJsonHttpClient().get(
                "/funds-api/account/v1/transactions?properties.record.some-key=one&properties.record.other-key=x"
            ) {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val responseTransaction1 =
            transactions.items.first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction1") } }
        assertThat(responseTransaction1.records).hasSize(2)
        assertThat(responseTransaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(responseTransaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(responseTransaction1.records[0].labels).containsExactlyInAnyOrder(Label("label1"))
        assertThat(responseTransaction1.records[0].properties.single { it.key == "some-key" }.value).isEqualTo("one")
        assertThat(responseTransaction1.records[0].properties.filter { it.key == "other-key" }
            .map { it.value }).containsExactly("x", "y")
        assertThat(responseTransaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(responseTransaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(responseTransaction1.records[1].labels).isEmpty()
        assertThat(responseTransaction1.records[1].properties.single { it.key == "some-key" }.value).isEqualTo("two")
        assertThat(responseTransaction1.records[1].properties.single { it.key == "other-key" }.value).isEqualTo("z")

        val responseTransaction2 =
            transactions.items.first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction2") } }
        assertThat(responseTransaction2.records).hasSize(1)
    }

    @Test
    fun `test get transactions by transaction property`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "one")
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "two")
                    )
                ),
                properties = propertiesOf(
                    "externalId" to "transaction1", "transactionProp" to "val1", "transactionProp" to "val2"
                )
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
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "one")
                    ),
                ),
                properties = propertiesOf(
                    "externalId" to "transaction2", "transactionProp" to "val1"
                )
            )
        )
        transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(12.5),
                        unit = Currency.RON,
                        properties = propertiesOf("some-key" to "two")
                    ),
                ),
                properties = propertiesOf(
                    "externalId" to "transaction3", "transactionProp" to "val2"
                )
            )
        )

        val response =
            createJsonHttpClient().get("/funds-api/account/v1/transactions?properties.transaction.transactionProp=val2") {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val responseTransaction1 =
            transactions.items.first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction1") } }
        assertThat(responseTransaction1.records).hasSize(2)
        assertThat(responseTransaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(responseTransaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(responseTransaction1.records[0].properties.single { it.key == "some-key" }.value).isEqualTo("one")
        assertThat(responseTransaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(responseTransaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(responseTransaction1.records[1].properties.single { it.key == "some-key" }.value).isEqualTo("two")

        val responseTransaction3 =
            transactions.items.first { t -> t.properties.any { it == PropertyTO("externalId" to "transaction3") } }
        assertThat(responseTransaction3.records).hasSize(1)
    }

    @Test
    fun `test delete transaction`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        val transaction = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("externalId" to "record1"),
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = propertiesOf("externalId" to "record2")
                    )
                ),
                properties = propertiesOf("externalId" to "transaction1")
            )
        )

        val response = createJsonHttpClient()
            .delete("/funds-api/account/v1/transactions/${transaction.id}") {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(transactionRepository.findById(userId, transaction.id)).isNull()
    }
}
