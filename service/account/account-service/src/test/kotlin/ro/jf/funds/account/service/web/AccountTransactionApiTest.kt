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
                    properties = mapOf("externalId" to listOf("record1")),
                ),
                CreateAccountRecordTO(
                    accountId = account2.id,
                    amount = BigDecimal("-123.45"),
                    unit = Currency.RON,
                    properties = mapOf("externalId" to listOf("record2")),
                )
            ),
            properties = mapOf("key" to listOf("val1", "val2"))
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
        assertThat(transaction.properties["key"]).isEqualTo(listOf("val1", "val2"))
        assertThat(transaction.records).hasSize(2)
        assertThat(transaction.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction.records[0].amount).isEqualTo(BigDecimal("123.45"))
        assertThat(transaction.records[0].properties["externalId"]).isEqualTo(listOf("record1"))
        assertThat(transaction.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction.records[1].amount).isEqualTo(BigDecimal("-123.45"))
        assertThat(transaction.records[1].properties["externalId"]).isEqualTo(listOf("record2"))
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
                        properties = mapOf(
                            "externalId" to listOf("record1"),
                            "someKey" to listOf("someValue", "otherValue")
                        )
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = mapOf("externalId" to listOf("record2"))
                    )
                ),
                properties = mapOf("externalId" to listOf("transaction1"), "transactionProp" to listOf("val1", "val2"))
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
                        properties = mapOf("externalId" to listOf("record3"))
                    ),
                ),
                properties = mapOf("externalId" to listOf("transaction2"))
            )
        )

        val response = createJsonHttpClient().get("/bk-api/account/v1/transactions") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val transaction1 = transactions.items.first { it.properties["externalId"]?.contains("transaction1") ?: false }
        assertThat(transaction1.records).hasSize(2)
        assertThat(transaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(transaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(transaction1.records[0].properties["externalId"]).isEqualTo(listOf("record1"))
        assertThat(transaction1.records[0].properties["someKey"]).containsExactlyInAnyOrder("someValue", "otherValue")
        assertThat(transaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(transaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(transaction1.records[1].properties["externalId"]).isEqualTo(listOf("record2"))
        assertThat(transaction1.properties["transactionProp"]).containsExactly("val1", "val2")

        val transaction2 = transactions.items.first { it.properties["externalId"]?.contains("transaction2") ?: false }
        assertThat(transaction2.records).hasSize(1)
    }

    @Test
    fun `test get transactions by record property`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        val transaction1 = transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("one"), "other-key" to listOf("x", "y"))
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("two"), "other-key" to listOf("z"))
                    )
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction1"),
                    "transactionProp" to listOf("val1", "val2")
                )
            )
        )
        val transaction2 = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(50.123),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("one"), "other-key" to listOf("x"))
                    ),
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction2"),
                    "transactionProp" to listOf("val3")
                )
            )
        )
        val transaction3 = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(12.5),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("two"), "other-key" to listOf("x"))
                    ),
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction3"),
                    "transactionProp" to listOf("val4")
                )
            )
        )

        val response =
            createJsonHttpClient().get(
                "/bk-api/account/v1/transactions?properties.record.some-key=one&properties.record.other-key=x"
            ) {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val responseTransaction1 =
            transactions.items.first { it.properties["externalId"]?.contains("transaction1") ?: false }
        assertThat(responseTransaction1.records).hasSize(2)
        assertThat(responseTransaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(responseTransaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(responseTransaction1.records[0].properties["some-key"]).containsExactly("one")
        assertThat(responseTransaction1.records[0].properties["other-key"]).containsExactly("x", "y")
        assertThat(responseTransaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(responseTransaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(responseTransaction1.records[1].properties["some-key"]).containsExactly("two")
        assertThat(responseTransaction1.records[1].properties["other-key"]).containsExactly("z")

        val responseTransaction2 =
            transactions.items.first { it.properties["externalId"]?.contains("transaction2") ?: false }
        assertThat(responseTransaction2.records).hasSize(1)
    }

    @Test
    fun `test get transactions by transaction property`() = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val dateTime = LocalDateTime(2024, 7, 22, 9, 17)
        val account1 = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))
        val account2 = accountRepository.save(userId, CreateAccountTO(AccountName("BT"), Currency.RON))
        val transaction1 = transactionRepository.save(
            userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(100.0),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("one"))
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("two"))
                    )
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction1"),
                    "transactionProp" to listOf("val1", "val2")
                )
            )
        )
        val transaction2 = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(50.123),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("one"))
                    ),
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction2"),
                    "transactionProp" to listOf("val1")
                )
            )
        )
        val transaction3 = transactionRepository.save(
            userId = userId,
            CreateAccountTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = account1.id,
                        amount = BigDecimal(12.5),
                        unit = Currency.RON,
                        properties = mapOf("some-key" to listOf("two"))
                    ),
                ),
                properties = mapOf(
                    "externalId" to listOf("transaction3"),
                    "transactionProp" to listOf("val2")
                )
            )
        )

        val response =
            createJsonHttpClient().get("/bk-api/account/v1/transactions?properties.transaction.transactionProp=val2") {
                header(USER_ID_HEADER, userId)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val transactions = response.body<ListTO<AccountTransactionTO>>()
        assertThat(transactions.items).hasSize(2)
        val responseTransaction1 =
            transactions.items.first { it.properties["externalId"]?.contains("transaction1") ?: false }
        assertThat(responseTransaction1.records).hasSize(2)
        assertThat(responseTransaction1.records[0].amount.compareTo(BigDecimal(100))).isZero()
        assertThat(responseTransaction1.records[0].accountId).isEqualTo(account1.id)
        assertThat(responseTransaction1.records[0].properties["some-key"]).containsExactly("one")
        assertThat(responseTransaction1.records[1].amount.compareTo(BigDecimal(-100))).isZero()
        assertThat(responseTransaction1.records[1].accountId).isEqualTo(account2.id)
        assertThat(responseTransaction1.records[1].properties["some-key"]).containsExactly("two")

        val responseTransaction3 =
            transactions.items.first { it.properties["externalId"]?.contains("transaction3") ?: false }
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
                        properties = mapOf("externalId" to listOf("record1")),
                    ),
                    CreateAccountRecordTO(
                        accountId = account2.id,
                        amount = BigDecimal(-100.0),
                        unit = Currency.RON,
                        properties = mapOf("externalId" to listOf("record2"))
                    )
                ),
                properties = mapOf("externalId" to listOf("transaction1"))
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
