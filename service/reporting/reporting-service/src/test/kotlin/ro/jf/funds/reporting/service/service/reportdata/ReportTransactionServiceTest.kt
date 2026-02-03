package ro.jf.funds.reporting.service.service.reportdata

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.platform.api.model.Currency.Companion.EUR
import ro.jf.funds.platform.api.model.Currency.Companion.RON
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.ListTO
import ro.jf.funds.reporting.service.domain.*
import java.util.UUID.randomUUID

class ReportTransactionServiceTest {
    private val transactionSdk = mock<TransactionSdk>()
    private val service = ReportTransactionService(transactionSdk)

    private val userId = randomUUID()
    private val fundA = randomUUID()
    private val fundB = randomUUID()

    @Test
    fun `given single record matching fund - when getting bucket transactions - then record is included`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.SingleRecord(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                record = currencyRecord(fundA, RON, -100),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).hasSize(1)
            assertThat(result[0]).isInstanceOf(ReportTransaction.SingleRecord::class.java)
            assertThat(result[0].records).hasSize(1)
            assertThat(result[0].records[0].fundId).isEqualTo(fundA)
        }

    @Test
    fun `given single record not matching fund - when getting bucket transactions - then transaction is excluded`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.SingleRecord(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                record = currencyRecord(fundB, RON, -50),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).isEmpty()
        }

    @Test
    fun `given transfer with cross-fund records - when getting bucket transactions - then only matching fund record is included`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.Transfer(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                sourceRecord = currencyRecord(fundA, RON, -200),
                destinationRecord = currencyRecord(fundB, RON, 200),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).hasSize(1)
            val transfer = result[0] as ReportTransaction.Transfer
            assertThat(transfer.sourceRecord).isNotNull
            assertThat(transfer.sourceRecord!!.fundId).isEqualTo(fundA)
            assertThat(transfer.destinationRecord).isNull()
            assertThat(transfer.records).hasSize(1)
        }

    @Test
    fun `given transfer with both records matching fund - when getting bucket transactions - then both records are included`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.Transfer(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                sourceRecord = currencyRecord(fundA, RON, -200),
                destinationRecord = currencyRecord(fundA, RON, 200),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).hasSize(1)
            val transfer = result[0] as ReportTransaction.Transfer
            assertThat(transfer.sourceRecord).isNotNull
            assertThat(transfer.destinationRecord).isNotNull
            assertThat(transfer.records).hasSize(2)
        }

    @Test
    fun `given exchange with cross-fund records - when getting bucket transactions - then only matching fund records are included`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.Exchange(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                sourceRecord = currencyRecord(fundA, RON, -500),
                destinationRecord = currencyRecord(fundB, EUR, 100),
                feeRecord = currencyRecord(fundA, RON, -5),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).hasSize(1)
            val exchange = result[0] as ReportTransaction.Exchange
            assertThat(exchange.sourceRecord).isNotNull
            assertThat(exchange.sourceRecord!!.fundId).isEqualTo(fundA)
            assertThat(exchange.destinationRecord).isNull()
            assertThat(exchange.feeRecord).isNotNull
            assertThat(exchange.feeRecord!!.fundId).isEqualTo(fundA)
            assertThat(exchange.records).hasSize(2)
        }

    @Test
    fun `given open position - when getting bucket transactions - then all records are included`(): Unit =
        runBlocking {
            val date = LocalDate(2024, 3, 15)
            val bucket = TimeBucket(LocalDate(2024, 3, 1), LocalDate(2024, 3, 31))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val transaction = TransactionTO.OpenPosition(
                id = randomUUID(),
                userId = userId,
                dateTime = date.atTime(12, 0),
                externalId = "ext-1",
                currencyRecord = currencyRecord(fundA, EUR, -1000),
                instrumentRecord = instrumentRecord(fundA, Instrument("AAPL"), 10),
            )
            mockTransactions(bucket, fundA, listOf(transaction))

            val result = service.getBucketReportTransactions(reportView, bucket)

            assertThat(result).hasSize(1)
            val position = result[0] as ReportTransaction.OpenPosition
            assertThat(position.currencyRecord.fundId).isEqualTo(fundA)
            assertThat(position.instrumentRecord.fundId).isEqualTo(fundA)
            assertThat(position.records).hasSize(2)
        }

    @Test
    fun `given previous transactions with mixed funds - when getting previous transactions - then only matching fund records are included`(): Unit =
        runBlocking {
            val interval = ReportDataInterval.Monthly(YearMonth(2024, 3), YearMonth(2024, 5))
            val reportView = ReportView(randomUUID(), userId, "view", fundA, reportDataConfiguration())
            val singleRecord = TransactionTO.SingleRecord(
                id = randomUUID(),
                userId = userId,
                dateTime = LocalDate(2024, 2, 10).atTime(12, 0),
                externalId = "ext-1",
                record = currencyRecord(fundA, RON, -100),
            )
            val crossFundTransfer = TransactionTO.Transfer(
                id = randomUUID(),
                userId = userId,
                dateTime = LocalDate(2024, 2, 20).atTime(12, 0),
                externalId = "ext-2",
                sourceRecord = currencyRecord(fundA, RON, -300),
                destinationRecord = currencyRecord(fundB, RON, 300),
            )
            val otherFundSingleRecord = TransactionTO.SingleRecord(
                id = randomUUID(),
                userId = userId,
                dateTime = LocalDate(2024, 2, 25).atTime(12, 0),
                externalId = "ext-3",
                record = currencyRecord(fundB, RON, -50),
            )

            whenever(
                transactionSdk.listTransactions(
                    userId,
                    TransactionFilterTO(null, interval.getPreviousLastDay(), fundA)
                )
            ).thenReturn(ListTO(listOf(singleRecord, crossFundTransfer, otherFundSingleRecord)))

            val result = service.getPreviousReportTransactions(reportView, interval)

            assertThat(result).hasSize(2)
            assertThat(result[0]).isInstanceOf(ReportTransaction.SingleRecord::class.java)
            assertThat(result[0].records[0].fundId).isEqualTo(fundA)
            assertThat(result[1]).isInstanceOf(ReportTransaction.Transfer::class.java)
            val transfer = result[1] as ReportTransaction.Transfer
            assertThat(transfer.sourceRecord).isNotNull
            assertThat(transfer.destinationRecord).isNull()
            assertThat(transfer.records).hasSize(1)
            assertThat(transfer.records[0].fundId).isEqualTo(fundA)
        }

    private fun reportDataConfiguration() = ReportDataConfiguration(
        currency = RON,
        groups = null,
        reports = ReportsConfiguration(),
    )

    private fun currencyRecord(
        fundId: java.util.UUID,
        currency: ro.jf.funds.platform.api.model.Currency,
        amount: Int,
    ) = TransactionRecordTO.CurrencyRecord(
        id = randomUUID(),
        accountId = randomUUID(),
        fundId = fundId,
        amount = BigDecimal.fromInt(amount),
        unit = currency,
        labels = emptyList(),
    )

    private fun instrumentRecord(
        fundId: java.util.UUID,
        instrument: Instrument,
        amount: Int,
    ) = TransactionRecordTO.InstrumentRecord(
        id = randomUUID(),
        accountId = randomUUID(),
        fundId = fundId,
        amount = BigDecimal.fromInt(amount),
        unit = instrument,
        labels = emptyList(),
    )

    private suspend fun mockTransactions(
        bucket: TimeBucket,
        fundId: java.util.UUID,
        transactions: List<TransactionTO>,
    ) {
        whenever(
            transactionSdk.listTransactions(
                userId,
                TransactionFilterTO(bucket.from, bucket.to, fundId)
            )
        ).thenReturn(ListTO(transactions))
    }
}
