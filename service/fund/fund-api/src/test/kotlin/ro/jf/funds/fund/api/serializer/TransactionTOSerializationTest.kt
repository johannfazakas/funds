package ro.jf.funds.fund.api.serializer

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import java.math.BigDecimal
import java.util.UUID.randomUUID

class TransactionTOSerializationTest {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `test serialize Transfer transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId1 = randomUUID()
        val fundId2 = randomUUID()

        val transaction = TransactionTO.Transfer(
            id = transactionId,
            userId = userId,
            dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
            externalId = "test-transfer",
            sourceRecord = TransactionRecordTO(
                id = recordId1,
                accountId = accountId1,
                fundId = fundId1,
                amount = BigDecimal("-100.25"),
                unit = Currency.RON,
                labels = listOf(Label("one"), Label("two"))
            ),
            destinationRecord = TransactionRecordTO(
                id = recordId2,
                accountId = accountId2,
                fundId = fundId2,
                amount = BigDecimal("100.25"),
                unit = Currency.RON,
                labels = emptyList()
            )
        )

        val jsonElement = json.encodeToJsonElement<TransactionTO>(transaction)
        val jsonObject = jsonElement.jsonObject

        assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("TRANSFER")
        assertThat(jsonObject["id"]?.jsonPrimitive?.content).isEqualTo(transactionId.toString())
        assertThat(jsonObject["userId"]?.jsonPrimitive?.content).isEqualTo(userId.toString())
        assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-01T12:00")
        assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-transfer")

        val sourceRecord = jsonObject["sourceRecord"]?.jsonObject
        assertThat(sourceRecord).isNotNull
        assertThat(sourceRecord!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId1.toString())
        assertThat(sourceRecord["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId1.toString())
        assertThat(sourceRecord["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId1.toString())
        assertThat(sourceRecord["amount"]?.jsonPrimitive?.content).isEqualTo("-100.25")

        val destinationRecord = jsonObject["destinationRecord"]?.jsonObject
        assertThat(destinationRecord).isNotNull
        assertThat(destinationRecord!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId2.toString())
        assertThat(destinationRecord["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId2.toString())
        assertThat(destinationRecord["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId2.toString())
        assertThat(destinationRecord["amount"]?.jsonPrimitive?.content).isEqualTo("100.25")
    }

    @Test
    fun `test serialize SingleRecord transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId = randomUUID()
        val accountId = randomUUID()
        val fundId = randomUUID()

        val transaction = TransactionTO.SingleRecord(
            id = transactionId,
            userId = userId,
            dateTime = LocalDateTime.parse("2021-09-02T15:30:00"),
            externalId = "test-single",
            record = TransactionRecordTO(
                id = recordId,
                accountId = accountId,
                fundId = fundId,
                amount = BigDecimal("50.00"),
                unit = Currency.EUR,
                labels = emptyList()
            )
        )

        val jsonElement = json.encodeToJsonElement<TransactionTO>(transaction)
        val jsonObject = jsonElement.jsonObject

        assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("SINGLE_RECORD")
        assertThat(jsonObject["id"]?.jsonPrimitive?.content).isEqualTo(transactionId.toString())
        assertThat(jsonObject["userId"]?.jsonPrimitive?.content).isEqualTo(userId.toString())
        assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-02T15:30")
        assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-single")

        val record = jsonObject["record"]?.jsonObject
        assertThat(record).isNotNull
        assertThat(record!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId.toString())
        assertThat(record["accountId"]?.jsonPrimitive?.content).isEqualTo(accountId.toString())
        assertThat(record["fundId"]?.jsonPrimitive?.content).isEqualTo(fundId.toString())
        assertThat(record["amount"]?.jsonPrimitive?.content).isEqualTo("50.0")
    }

    @Test
    fun `test serialize Exchange transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val recordId3 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val transaction = TransactionTO.Exchange(
            id = transactionId,
            userId = userId,
            dateTime = LocalDateTime.parse("2021-09-03T10:00:00"),
            externalId = "test-exchange",
            sourceRecord = TransactionRecordTO(
                id = recordId1,
                accountId = accountId1,
                fundId = fundId,
                amount = BigDecimal("-100.00"),
                unit = Currency.USD,
                labels = emptyList()
            ),
            destinationRecord = TransactionRecordTO(
                id = recordId2,
                accountId = accountId2,
                fundId = fundId,
                amount = BigDecimal("85.00"),
                unit = Currency.EUR,
                labels = emptyList()
            ),
            feeRecord = TransactionRecordTO(
                id = recordId3,
                accountId = accountId1,
                fundId = fundId,
                amount = BigDecimal("-2.50"),
                unit = Currency.USD,
                labels = emptyList()
            )
        )

        val jsonElement = json.encodeToJsonElement<TransactionTO>(transaction)
        val jsonObject = jsonElement.jsonObject

        assertThat(jsonObject["type"]?.jsonPrimitive?.content).isEqualTo("EXCHANGE")
        assertThat(jsonObject["id"]?.jsonPrimitive?.content).isEqualTo(transactionId.toString())
        assertThat(jsonObject["userId"]?.jsonPrimitive?.content).isEqualTo(userId.toString())
        assertThat(jsonObject["dateTime"]?.jsonPrimitive?.content).isEqualTo("2021-09-03T10:00")
        assertThat(jsonObject["externalId"]?.jsonPrimitive?.content).isEqualTo("test-exchange")

        val sourceRecord = jsonObject["sourceRecord"]?.jsonObject
        assertThat(sourceRecord).isNotNull
        assertThat(sourceRecord!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId1.toString())
        assertThat(sourceRecord["amount"]?.jsonPrimitive?.content).isEqualTo("-100.0")

        val destinationRecord = jsonObject["destinationRecord"]?.jsonObject
        assertThat(destinationRecord).isNotNull
        assertThat(destinationRecord!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId2.toString())
        assertThat(destinationRecord["amount"]?.jsonPrimitive?.content).isEqualTo("85.0")

        val feeRecord = jsonObject["feeRecord"]?.jsonObject
        assertThat(feeRecord).isNotNull
        assertThat(feeRecord!!["id"]?.jsonPrimitive?.content).isEqualTo(recordId3.toString())
        assertThat(feeRecord["amount"]?.jsonPrimitive?.content).isEqualTo("-2.5")
    }

    @Test
    fun `test deserialize Transfer transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId1 = randomUUID()
        val fundId2 = randomUUID()

        val jsonString = """
            {
                "type": "TRANSFER",
                "id": "$transactionId",
                "userId": "$userId",
                "dateTime": "2021-09-01T12:00:00",
                "externalId": "test-transfer",
                "sourceRecord": {
                    "id": "$recordId1",
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
                    "id": "$recordId2",
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

        val transaction = json.decodeFromString<TransactionTO>(jsonString)

        assertThat(transaction).isInstanceOf(TransactionTO.Transfer::class.java)
        val transfer = transaction as TransactionTO.Transfer
        assertThat(transfer.id).isEqualTo(transactionId)
        assertThat(transfer.userId).isEqualTo(userId)
        assertThat(transfer.dateTime).isEqualTo(LocalDateTime.parse("2021-09-01T12:00:00"))
        assertThat(transfer.externalId).isEqualTo("test-transfer")
        assertThat(transfer.sourceRecord.id).isEqualTo(recordId1)
        assertThat(transfer.sourceRecord.accountId).isEqualTo(accountId1)
        assertThat(transfer.sourceRecord.fundId).isEqualTo(fundId1)
        assertThat(transfer.sourceRecord.amount).isEqualByComparingTo(BigDecimal("-100.25"))
        assertThat(transfer.sourceRecord.unit).isEqualTo(Currency.RON)
        assertThat(transfer.sourceRecord.labels).containsExactly(Label("one"), Label("two"))
        assertThat(transfer.destinationRecord.id).isEqualTo(recordId2)
        assertThat(transfer.destinationRecord.accountId).isEqualTo(accountId2)
        assertThat(transfer.destinationRecord.fundId).isEqualTo(fundId2)
        assertThat(transfer.destinationRecord.amount).isEqualByComparingTo(BigDecimal("100.25"))
        assertThat(transfer.destinationRecord.unit).isEqualTo(Currency.RON)
        assertThat(transfer.destinationRecord.labels).isEmpty()
    }

    @Test
    fun `test deserialize SingleRecord transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId = randomUUID()
        val accountId = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "SINGLE_RECORD",
                "id": "$transactionId",
                "userId": "$userId",
                "dateTime": "2021-09-02T15:30:00",
                "externalId": "test-single",
                "record": {
                    "id": "$recordId",
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

        val transaction = json.decodeFromString<TransactionTO>(jsonString)

        assertThat(transaction).isInstanceOf(TransactionTO.SingleRecord::class.java)
        val singleRecord = transaction as TransactionTO.SingleRecord
        assertThat(singleRecord.id).isEqualTo(transactionId)
        assertThat(singleRecord.userId).isEqualTo(userId)
        assertThat(singleRecord.dateTime).isEqualTo(LocalDateTime.parse("2021-09-02T15:30:00"))
        assertThat(singleRecord.externalId).isEqualTo("test-single")
        assertThat(singleRecord.record.id).isEqualTo(recordId)
        assertThat(singleRecord.record.accountId).isEqualTo(accountId)
        assertThat(singleRecord.record.fundId).isEqualTo(fundId)
        assertThat(singleRecord.record.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(singleRecord.record.unit).isEqualTo(Currency.EUR)
    }

    @Test
    fun `test deserialize Exchange transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val recordId3 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "EXCHANGE",
                "id": "$transactionId",
                "userId": "$userId",
                "dateTime": "2021-09-03T10:00:00",
                "externalId": "test-exchange",
                "sourceRecord": {
                    "id": "$recordId1",
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
                    "id": "$recordId2",
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
                    "id": "$recordId3",
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

        val transaction = json.decodeFromString<TransactionTO>(jsonString)

        assertThat(transaction).isInstanceOf(TransactionTO.Exchange::class.java)
        val exchange = transaction as TransactionTO.Exchange
        assertThat(exchange.id).isEqualTo(transactionId)
        assertThat(exchange.userId).isEqualTo(userId)
        assertThat(exchange.dateTime).isEqualTo(LocalDateTime.parse("2021-09-03T10:00:00"))
        assertThat(exchange.externalId).isEqualTo("test-exchange")
        assertThat(exchange.sourceRecord.id).isEqualTo(recordId1)
        assertThat(exchange.sourceRecord.accountId).isEqualTo(accountId1)
        assertThat(exchange.sourceRecord.amount).isEqualByComparingTo(BigDecimal("-100.00"))
        assertThat(exchange.sourceRecord.unit).isEqualTo(Currency.USD)
        assertThat(exchange.destinationRecord.id).isEqualTo(recordId2)
        assertThat(exchange.destinationRecord.accountId).isEqualTo(accountId2)
        assertThat(exchange.destinationRecord.amount).isEqualByComparingTo(BigDecimal("85.00"))
        assertThat(exchange.destinationRecord.unit).isEqualTo(Currency.EUR)
        assertThat(exchange.feeRecord).isNotNull
        assertThat(exchange.feeRecord!!.id).isEqualTo(recordId3)
        assertThat(exchange.feeRecord!!.amount).isEqualByComparingTo(BigDecimal("-2.50"))
        assertThat(exchange.feeRecord!!.unit).isEqualTo(Currency.USD)
    }

    @Test
    fun `test deserialize OpenPosition transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "OPEN_POSITION",
                "id": "$transactionId",
                "userId": "$userId",
                "dateTime": "2021-09-04T09:15:00",
                "externalId": "test-open-position",
                "currencyRecord": {
                    "id": "$recordId1",
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
                    "id": "$recordId2",
                    "accountId": "$accountId2",
                    "fundId": "$fundId",
                    "amount": "10",
                    "unit": {
                        "value": "AAPL",
                        "type": "instrument"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<TransactionTO>(jsonString)

        assertThat(transaction).isInstanceOf(TransactionTO.OpenPosition::class.java)
        val openPosition = transaction as TransactionTO.OpenPosition
        assertThat(openPosition.id).isEqualTo(transactionId)
        assertThat(openPosition.userId).isEqualTo(userId)
        assertThat(openPosition.dateTime).isEqualTo(LocalDateTime.parse("2021-09-04T09:15:00"))
        assertThat(openPosition.externalId).isEqualTo("test-open-position")
        assertThat(openPosition.currencyRecord.id).isEqualTo(recordId1)
        assertThat(openPosition.currencyRecord.accountId).isEqualTo(accountId1)
        assertThat(openPosition.currencyRecord.amount).isEqualByComparingTo(BigDecimal("-1000.00"))
        assertThat(openPosition.currencyRecord.unit).isEqualTo(Currency.USD)
        assertThat(openPosition.instrumentRecord.id).isEqualTo(recordId2)
        assertThat(openPosition.instrumentRecord.accountId).isEqualTo(accountId2)
        assertThat(openPosition.instrumentRecord.amount).isEqualByComparingTo(BigDecimal("10"))
        assertThat(openPosition.instrumentRecord.unit).isEqualTo(Instrument("AAPL"))
    }

    @Test
    fun `test deserialize ClosePosition transaction`() {
        val transactionId = randomUUID()
        val userId = randomUUID()
        val recordId1 = randomUUID()
        val recordId2 = randomUUID()
        val accountId1 = randomUUID()
        val accountId2 = randomUUID()
        val fundId = randomUUID()

        val jsonString = """
            {
                "type": "CLOSE_POSITION",
                "id": "$transactionId",
                "userId": "$userId",
                "dateTime": "2021-09-05T16:45:00",
                "externalId": "test-close-position",
                "currencyRecord": {
                    "id": "$recordId1",
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
                    "id": "$recordId2",
                    "accountId": "$accountId2",
                    "fundId": "$fundId",
                    "amount": "-10",
                    "unit": {
                        "value": "AAPL",
                        "type": "instrument"
                    },
                    "labels": []
                }
            }
        """.trimIndent()

        val transaction = json.decodeFromString<TransactionTO>(jsonString)

        assertThat(transaction).isInstanceOf(TransactionTO.ClosePosition::class.java)
        val closePosition = transaction as TransactionTO.ClosePosition
        assertThat(closePosition.id).isEqualTo(transactionId)
        assertThat(closePosition.userId).isEqualTo(userId)
        assertThat(closePosition.dateTime).isEqualTo(LocalDateTime.parse("2021-09-05T16:45:00"))
        assertThat(closePosition.externalId).isEqualTo("test-close-position")
        assertThat(closePosition.currencyRecord.id).isEqualTo(recordId1)
        assertThat(closePosition.currencyRecord.accountId).isEqualTo(accountId1)
        assertThat(closePosition.currencyRecord.amount).isEqualByComparingTo(BigDecimal("1100.00"))
        assertThat(closePosition.currencyRecord.unit).isEqualTo(Currency.USD)
        assertThat(closePosition.instrumentRecord.id).isEqualTo(recordId2)
        assertThat(closePosition.instrumentRecord.accountId).isEqualTo(accountId2)
        assertThat(closePosition.instrumentRecord.amount).isEqualByComparingTo(BigDecimal("-10"))
        assertThat(closePosition.instrumentRecord.unit).isEqualTo(Instrument("AAPL"))
    }
}
