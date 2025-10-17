package ro.jf.funds.fund.api.serializer

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import java.math.BigDecimal
import java.util.UUID
import java.util.UUID.randomUUID

class CreateTransactionTOSerializationTest {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `test serialize Transfer transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId1 = randomUUID()
        val fundId2 = randomUUID()

        val request = CreateTransactionTO.Transfer(
            dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
            externalId = "test-transfer",
            sourceRecord = CreateTransactionRecordTO(
                accountId = accountId1,
                fundId = fundId1,
                amount = BigDecimal("-100.25"),
                unit = Currency.RON,
                labels = listOf(Label("one"), Label("two"))
            ),
            destinationRecord = CreateTransactionRecordTO(
                accountId = accountId2,
                fundId = fundId2,
                amount = BigDecimal("100.25"),
                unit = Currency.RON
            )
        )

        val jsonElement = json.encodeToJsonElement<CreateTransactionTO>(request)
        val jsonObject = jsonElement.jsonObject

        Assertions.assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("TRANSFER")
        Assertions.assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-01T12:00:00")
        Assertions.assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-transfer")

        val sourceRecord = jsonObject["sourceRecord"]?.jsonObject
        Assertions.assertThat(sourceRecord).isNotNull
        Assertions.assertThat(sourceRecord!!["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId1.toString())
        Assertions.assertThat(sourceRecord["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId1.toString())
        Assertions.assertThat(sourceRecord["amount"]?.jsonPrimitive?.content).isEqualTo("-100.25")

        val destinationRecord = jsonObject["destinationRecord"]?.jsonObject
        Assertions.assertThat(destinationRecord).isNotNull
        Assertions.assertThat(destinationRecord!!["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId2.toString())
        Assertions.assertThat(destinationRecord["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId2.toString())
        Assertions.assertThat(destinationRecord["amount"]?.jsonPrimitive?.content).isEqualTo("100.25")
    }

    @Test
    fun `test serialize SingleRecord transaction`() {
        val accountId = randomUUID()
        val fundId = randomUUID()

        val request = CreateTransactionTO.SingleRecord(
            dateTime = LocalDateTime.parse("2021-09-02T15:30:00"),
            externalId = "test-single",
            record = CreateTransactionRecordTO(
                accountId = accountId,
                fundId = fundId,
                amount = BigDecimal("50.00"),
                unit = Currency.EUR
            )
        )

        val jsonElement = json.encodeToJsonElement<CreateTransactionTO>(request)
        val jsonObject = jsonElement.jsonObject

        Assertions.assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("SINGLE_RECORD")
        Assertions.assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-02T15:30:00")
        Assertions.assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-single")

        val record = jsonObject["record"]?.jsonObject
        Assertions.assertThat(record).isNotNull
        Assertions.assertThat(record!!["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId.toString())
        Assertions.assertThat(record["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId.toString())
        Assertions.assertThat(record["amount"]?.jsonPrimitive?.content).isEqualTo("50.0")
    }

    @Test
    fun `test serialize Exchange transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val request = CreateTransactionTO.Exchange(
            dateTime = LocalDateTime.parse("2021-09-03T10:00:00"),
            externalId = "test-exchange",
            sourceRecord = CreateTransactionRecordTO(
                accountId = accountId1,
                fundId = fundId,
                amount = BigDecimal("-100.00"),
                unit = Currency.USD
            ),
            destinationRecord = CreateTransactionRecordTO(
                accountId = accountId2,
                fundId = fundId,
                amount = BigDecimal("85.00"),
                unit = Currency.EUR
            ),
            feeRecord = CreateTransactionRecordTO(
                accountId = accountId1,
                fundId = fundId,
                amount = BigDecimal("-2.50"),
                unit = Currency.USD
            )
        )

        val jsonElement = json.encodeToJsonElement<CreateTransactionTO>(request)
        val jsonObject = jsonElement.jsonObject

        Assertions.assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("EXCHANGE")
        Assertions.assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-03T10:00:00")
        Assertions.assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-exchange")

        val sourceRecord = jsonObject["sourceRecord"]?.jsonObject
        Assertions.assertThat(sourceRecord).isNotNull
        Assertions.assertThat(sourceRecord!!["amount"]?.jsonPrimitive?.content).isEqualTo("-100.0")

        val destinationRecord = jsonObject["destinationRecord"]?.jsonObject
        Assertions.assertThat(destinationRecord).isNotNull
        Assertions.assertThat(destinationRecord!!["amount"]?.jsonPrimitive?.content).isEqualTo("85.0")

        val feeRecord = jsonObject["feeRecord"]?.jsonObject
        Assertions.assertThat(feeRecord).isNotNull
        Assertions.assertThat(feeRecord!!["amount"]?.jsonPrimitive?.content).isEqualTo("-2.5")
    }

    @Test
    fun `test deserialize Transfer transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId1 = randomUUID()
        val fundId2 = randomUUID()

        val jsonString = """
            {
                "type": "TRANSFER",
                "dateTime": "2021-09-01T12:00:00",
                "externalId": "test-transfer",
                "sourceRecord": {
                    "accountId": "$accountId1",
                    "fundId": "$fundId1",
                    "amount": "-100.25",
                    "unit": {
                        "value": "RON",
                        "type": "currency"
                    },
                    "labels": ["one", "two"]
                },
                "destinationRecord": {
                    "accountId": "$accountId2",
                    "fundId": "$fundId2",
                    "amount": "100.25",
                    "unit": {
                        "value": "RON",
                        "type": "currency"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<CreateTransactionTO>(jsonString)

        Assertions.assertThat(transaction).isInstanceOf(CreateTransactionTO.Transfer::class.java)
        val transfer = transaction as CreateTransactionTO.Transfer
        Assertions.assertThat(transfer.dateTime).isEqualTo(LocalDateTime.parse("2021-09-01T12:00:00"))
        Assertions.assertThat(transfer.externalId).isEqualTo("test-transfer")
        Assertions.assertThat(transfer.sourceRecord.accountId).isEqualTo(accountId1)
        Assertions.assertThat(transfer.sourceRecord.fundId).isEqualTo(fundId1)
        Assertions.assertThat(transfer.sourceRecord.amount).isEqualTo(BigDecimal("-100.25"))
        Assertions.assertThat(transfer.sourceRecord.unit).isEqualTo(Currency.RON)
        Assertions.assertThat(transfer.sourceRecord.labels).containsExactly(Label("one"), Label("two"))
        Assertions.assertThat(transfer.destinationRecord.accountId).isEqualTo(accountId2)
        Assertions.assertThat(transfer.destinationRecord.fundId).isEqualTo(fundId2)
        Assertions.assertThat(transfer.destinationRecord.amount).isEqualTo(BigDecimal("100.25"))
        Assertions.assertThat(transfer.destinationRecord.unit).isEqualTo(Currency.RON)
        Assertions.assertThat(transfer.destinationRecord.labels).isEmpty()
    }

    @Test
    fun `test deserialize SingleRecord transaction`() {
        val accountId = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "SINGLE_RECORD",
                "dateTime": "2021-09-02T15:30:00",
                "externalId": "test-single",
                "record": {
                    "accountId": "$accountId",
                    "fundId": "$fundId",
                    "amount": "50.00",
                    "unit": {
                        "value": "EUR",
                        "type": "currency"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<CreateTransactionTO>(jsonString)

        Assertions.assertThat(transaction).isInstanceOf(CreateTransactionTO.SingleRecord::class.java)
        val singleRecord = transaction as CreateTransactionTO.SingleRecord
        Assertions.assertThat(singleRecord.dateTime).isEqualTo(LocalDateTime.parse("2021-09-02T15:30:00"))
        Assertions.assertThat(singleRecord.externalId).isEqualTo("test-single")
        Assertions.assertThat(singleRecord.record.accountId).isEqualTo(accountId)
        Assertions.assertThat(singleRecord.record.fundId).isEqualTo(fundId)
        Assertions.assertThat(singleRecord.record.amount).isEqualByComparingTo(BigDecimal("50.00"))
        Assertions.assertThat(singleRecord.record.unit).isEqualTo(Currency.EUR)
    }

    @Test
    fun `test deserialize Exchange transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "EXCHANGE",
                "dateTime": "2021-09-03T10:00:00",
                "externalId": "test-exchange",
                "sourceRecord": {
                    "accountId": "$accountId1",
                    "fundId": "$fundId",
                    "amount": "-100.00",
                    "unit": {
                        "value": "USD",
                        "type": "currency"
                    },
                    "labels": []
                },
                "destinationRecord": {
                    "accountId": "$accountId2",
                    "fundId": "$fundId",
                    "amount": "85.00",
                    "unit": {
                        "value": "EUR",
                        "type": "currency"
                    },
                    "labels": []
                },
                "feeRecord": {
                    "accountId": "$accountId1",
                    "fundId": "$fundId",
                    "amount": "-2.50",
                    "unit": {
                        "value": "USD",
                        "type": "currency"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<CreateTransactionTO>(jsonString)

        Assertions.assertThat(transaction).isInstanceOf(CreateTransactionTO.Exchange::class.java)
        val exchange = transaction as CreateTransactionTO.Exchange
        Assertions.assertThat(exchange.dateTime).isEqualTo(LocalDateTime.parse("2021-09-03T10:00:00"))
        Assertions.assertThat(exchange.externalId).isEqualTo("test-exchange")
        Assertions.assertThat(exchange.sourceRecord.accountId).isEqualTo(accountId1)
        Assertions.assertThat(exchange.sourceRecord.amount).isEqualByComparingTo(BigDecimal("-100.00"))
        Assertions.assertThat(exchange.sourceRecord.unit).isEqualTo(Currency.USD)
        Assertions.assertThat(exchange.destinationRecord.accountId).isEqualTo(accountId2)
        Assertions.assertThat(exchange.destinationRecord.amount).isEqualByComparingTo(BigDecimal("85.00"))
        Assertions.assertThat(exchange.destinationRecord.unit).isEqualTo(Currency.EUR)
        Assertions.assertThat(exchange.feeRecord).isNotNull
        Assertions.assertThat(exchange.feeRecord!!.amount).isEqualByComparingTo(BigDecimal("-2.50"))
        Assertions.assertThat(exchange.feeRecord!!.unit).isEqualTo(Currency.USD)
    }

    @Test
    fun `test deserialize OpenPosition transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "OPEN_POSITION",
                "dateTime": "2021-09-04T09:15:00",
                "externalId": "test-open-position",
                "currencyRecord": {
                    "accountId": "$accountId1",
                    "fundId": "$fundId",
                    "amount": "-1000.00",
                    "unit": {
                        "value": "USD",
                        "type": "currency"
                    },
                    "labels": []
                },
                "instrumentRecord": {
                    "accountId": "$accountId2",
                    "fundId": "$fundId",
                    "amount": "10",
                    "unit": {
                        "value": "AAPL",
                        "type": "symbol"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<CreateTransactionTO>(jsonString)

        Assertions.assertThat(transaction).isInstanceOf(CreateTransactionTO.OpenPosition::class.java)
        val openPosition = transaction as CreateTransactionTO.OpenPosition
        Assertions.assertThat(openPosition.dateTime).isEqualTo(LocalDateTime.parse("2021-09-04T09:15:00"))
        Assertions.assertThat(openPosition.externalId).isEqualTo("test-open-position")
        Assertions.assertThat(openPosition.currencyRecord.accountId).isEqualTo(accountId1)
        Assertions.assertThat(openPosition.currencyRecord.amount).isEqualByComparingTo(BigDecimal("-1000.00"))
        Assertions.assertThat(openPosition.currencyRecord.unit).isEqualTo(Currency.USD)
        Assertions.assertThat(openPosition.instrumentRecord.accountId).isEqualTo(accountId2)
        Assertions.assertThat(openPosition.instrumentRecord.amount).isEqualByComparingTo(BigDecimal("10"))
        Assertions.assertThat(openPosition.instrumentRecord.unit).isEqualTo(Symbol("AAPL"))
    }

    @Test
    fun `test deserialize ClosePosition transaction`() {
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "CLOSE_POSITION",
                "dateTime": "2021-09-05T16:45:00",
                "externalId": "test-close-position",
                "currencyRecord": {
                    "accountId": "$accountId1",
                    "fundId": "$fundId",
                    "amount": "1100.00",
                    "unit": {
                        "value": "USD",
                        "type": "currency"
                    },
                    "labels": []
                },
                "instrumentRecord": {
                    "accountId": "$accountId2",
                    "fundId": "$fundId",
                    "amount": "-10",
                    "unit": {
                        "value": "AAPL",
                        "type": "symbol"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<CreateTransactionTO>(jsonString)

        Assertions.assertThat(transaction).isInstanceOf(CreateTransactionTO.ClosePosition::class.java)
        val closePosition = transaction as CreateTransactionTO.ClosePosition
        Assertions.assertThat(closePosition.dateTime).isEqualTo(LocalDateTime.parse("2021-09-05T16:45:00"))
        Assertions.assertThat(closePosition.externalId).isEqualTo("test-close-position")
        Assertions.assertThat(closePosition.currencyRecord.accountId).isEqualTo(accountId1)
        Assertions.assertThat(closePosition.currencyRecord.amount).isEqualByComparingTo(BigDecimal("1100.00"))
        Assertions.assertThat(closePosition.currencyRecord.unit).isEqualTo(Currency.USD)
        Assertions.assertThat(closePosition.instrumentRecord.accountId).isEqualTo(accountId2)
        Assertions.assertThat(closePosition.instrumentRecord.amount).isEqualByComparingTo(BigDecimal("-10"))
        Assertions.assertThat(closePosition.instrumentRecord.unit).isEqualTo(Symbol("AAPL"))
    }
}