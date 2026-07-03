package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.AnalyticsInputRecordFilter
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.domain.BucketedGroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.InterestRateCalculator
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument

class InterestRateServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val conversionSdk = mock<ConversionSdk>()
    private val interestRateCalculator = InterestRateCalculator()
    private val service = InterestRateService(analyticsRecordRepository, conversionSdk, interestRateCalculator)

    private val userId = uuid4()
    private val fundId = uuid4()
    private val accountId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.YEARLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2025-01-01T00:00:00"),
    )

    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    @BeforeEach
    fun setupConversionSdkMock(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { req ->
                val rate = if (req.sourceUnit == req.targetCurrency) BigDecimal.ONE
                else mockRates[Triple(req.sourceUnit, req.targetCurrency, req.date)]
                    ?: error("No mock rate for $req")
                ConversionResponse(req.sourceUnit, req.targetCurrency, req.date, rate)
            })
        }
    }

    private fun ungroupedAmounts(amounts: UnitAmounts) =
        GroupedUnitAmounts(mapOf(GroupKey.Ungrouped to amounts))

    @Test
    fun `given investment with 10 percent growth - when getting interest rate report - then returns approximately 10 percent`(): Unit = runBlocking {
        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")

        whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), any()))
            .thenReturn(listOf(
                AnalyticsRecord(
                    id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                    transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    amount = BigDecimal.parseString("-1000.00"),
                    unit = eur, category = null,
                )
            ))

        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00")))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), isNull()))
            .thenReturn(BucketedGroupedUnitAmounts(emptyMap()))
        whenever(analyticsRecordRepository.getRecords(any(), any(), any()))
            .thenReturn(emptyList())

        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("110.00")

        val report = service.getInterestRateReport(userId, interval, AnalyticsInputRecordFilter(), eur)

        assertThat(report.granularity).isEqualTo(TimeGranularity.YEARLY)
        assertThat(report.buckets).hasSize(1)
        assertThat(report.buckets[0].groups[0].value.totalInterestRate.doubleValue(false))
            .isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.5))
    }

    @Test
    fun `given no groupBy - when getting interest rate report - then returns ungrouped report with UNGROUPED group key`(): Unit = runBlocking {
        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")

        whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), any()))
            .thenReturn(listOf(
                AnalyticsRecord(
                    id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                    transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    amount = BigDecimal.parseString("-1000.00"),
                    unit = eur, category = null,
                )
            ))

        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00")))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), isNull()))
            .thenReturn(BucketedGroupedUnitAmounts(emptyMap()))
        whenever(analyticsRecordRepository.getRecords(any(), any(), any()))
            .thenReturn(emptyList())

        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("110.00")

        val report = service.getInterestRateReport(userId, interval, AnalyticsInputRecordFilter(), eur, groupBy = null)

        assertThat(report.buckets).hasSize(1)
        assertThat(report.buckets[0].groups).hasSize(1)
        assertThat(report.buckets[0].groups[0].groupKey).isEqualTo("UNGROUPED")
        assertThat(report.buckets[0].groups[0].value.totalInterestRate.doubleValue(false))
            .isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.5))
    }

    @Test
    fun `given two funds with different growth - when getting grouped interest rate report by fund - then returns per-fund rates`(): Unit = runBlocking {
        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")
        val vt = Instrument("VT")
        val fund2Id = uuid4()
        val fund1Str = fundId.toString()
        val fund2Str = fund2Id.toString()

        whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), any()))
            .thenReturn(listOf(
                AnalyticsRecord(
                    id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                    transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    amount = BigDecimal.parseString("-1000.00"),
                    unit = eur, category = null,
                ),
                AnalyticsRecord(
                    id = uuid4(), userId = userId, fundId = fund2Id, accountId = accountId,
                    transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    amount = BigDecimal.parseString("-2000.00"),
                    unit = eur, category = null,
                ),
            ))

        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), eq(GroupingCriteria.FUND)))
            .thenReturn(GroupedUnitAmounts(mapOf(
                GroupKey.ByFund(fund1Str) to UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00"))),
                GroupKey.ByFund(fund2Str) to UnitAmounts(mapOf(vt to BigDecimal.parseString("20.00"))),
            )))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), eq(GroupingCriteria.FUND)))
            .thenReturn(BucketedGroupedUnitAmounts(emptyMap()))
        whenever(analyticsRecordRepository.getRecords(any(), any(), any()))
            .thenReturn(emptyList())

        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("110.00")
        mockRates[Triple(vt, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("120.00")

        val report = service.getInterestRateReport(userId, interval, AnalyticsInputRecordFilter(), eur, groupBy = GroupingCriteria.FUND)

        assertThat(report.buckets).hasSize(1)
        val groups = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(groups).hasSize(2)

        val g1 = groups.first { it.groupKey == fund1Str }
        val g2 = groups.first { it.groupKey == fund2Str }
        assertThat(g1.value.totalInterestRate.doubleValue(false))
            .isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.5))
        assertThat(g2.value.totalInterestRate.doubleValue(false))
            .isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.5))
    }
}
