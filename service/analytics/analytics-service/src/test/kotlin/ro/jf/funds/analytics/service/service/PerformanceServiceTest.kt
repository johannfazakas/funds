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
import ro.jf.funds.analytics.service.domain.AnalyticsDbRecordFilter
import ro.jf.funds.analytics.service.domain.BucketedGroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
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

class PerformanceServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val conversionSdk = mock<ConversionSdk>()
    private val service = PerformanceService(analyticsRecordRepository, conversionSdk)

    private val userId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-03-01T00:00:00"),
    )

    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    @BeforeEach
    fun setupConversionSdkMock(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { req ->
                val rate = if (req.sourceUnit == req.targetCurrency) {
                    BigDecimal.ONE
                } else {
                    mockRates[Triple(req.sourceUnit, req.targetCurrency, req.date)]
                        ?: error("No mock rate configured for $req")
                }
                ConversionResponse(req.sourceUnit, req.targetCurrency, req.date, rate)
            })
        }
    }

    private fun givenRate(source: FinancialUnit, target: Currency, date: String, rate: String) {
        mockRates[Triple(source, target, LocalDate.parse(date))] = BigDecimal.parseString(rate)
    }

    private val vt = Instrument("VT")
    private val investmentFilter = AnalyticsDbRecordFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION))
    private val instrumentFilter = AnalyticsDbRecordFilter(
        transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION)
    )
    private val currencyFilter = AnalyticsDbRecordFilter()

    private fun ungroupedBuckets(vararg entries: Pair<LocalDateTime, UnitAmounts>) =
        BucketedGroupedUnitAmounts(entries.associate { (dt, ua) -> dt to GroupedUnitAmounts(mapOf(GroupKey.Ungrouped to ua)) })

    private fun ungroupedAmounts(amounts: UnitAmounts) =
        GroupedUnitAmounts(mapOf(GroupKey.Ungrouped to amounts))

    @Test
    fun `given open position records - when getting performance report - then returns correct investment and instrument values`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))

        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-1000.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    vt to BigDecimal.parseString("10.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-1000.00")
                )),
            ))

        givenRate(vt, Currency.RON, "2024-01-01", "120.00")
        givenRate(vt, Currency.RON, "2024-02-01", "130.00")

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(2)

        val jan = report.buckets[0].groups[0].value
        assertThat(jan.totalInvestment).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(jan.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1200.00"))
        assertThat(jan.totalProfit).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(jan.currencyValue).isEqualTo(BigDecimal.parseString("-1000.00"))
        assertThat(jan.currentInvestment).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(jan.currentProfit).isEqualTo(BigDecimal.parseString("200.00"))

        val feb = report.buckets[1].groups[0].value
        assertThat(feb.totalInvestment).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(feb.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1300.00"))
        assertThat(feb.totalProfit).isEqualTo(BigDecimal.parseString("300.00"))
        assertThat(feb.currencyValue).isEqualTo(BigDecimal.parseString("-1000.00"))
        assertThat(feb.currentInvestment).isEqualTo(BigDecimal.ZERO)
        assertThat(feb.currentProfit).isEqualTo(BigDecimal.parseString("100.00"))
    }

    @Test
    fun `given accumulating positions over buckets - when getting performance report - then accumulates investment and instruments`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00")))))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(vt to BigDecimal.parseString("5.00")))))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00")))))

        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-02-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-300.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-02-01T00:00:00") to UnitAmounts(mapOf(
                    vt to BigDecimal.parseString("3.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-02-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-300.00")
                )),
            ))

        givenRate(vt, Currency.RON, "2024-01-01", "110.00")
        givenRate(vt, Currency.RON, "2024-02-01", "120.00")

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(2)

        val jan = report.buckets[0].groups[0].value
        assertThat(jan.totalInvestment).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(jan.totalInstrumentValue).isEqualTo(BigDecimal.parseString("550.00"))
        assertThat(jan.totalProfit).isEqualTo(BigDecimal.parseString("50.00"))
        assertThat(jan.currencyValue).isEqualTo(BigDecimal.parseString("-500.00"))

        val feb = report.buckets[1].groups[0].value
        assertThat(feb.totalInvestment).isEqualTo(BigDecimal.parseString("800.00"))
        assertThat(feb.totalInstrumentValue).isEqualTo(BigDecimal.parseString("960.00"))
        assertThat(feb.totalProfit).isEqualTo(BigDecimal.parseString("160.00"))
        assertThat(feb.currencyValue).isEqualTo(BigDecimal.parseString("-800.00"))
    }

    @Test
    fun `given no records - when getting performance report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), isNull()))
            .thenReturn(BucketedGroupedUnitAmounts.EMPTY)

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(2)
        assertThat(report.buckets).allMatch {
            val value = it.groups[0].value
            value.totalInvestment == BigDecimal.ZERO &&
                value.totalInstrumentValue == BigDecimal.ZERO &&
                value.totalProfit == BigDecimal.ZERO &&
                value.currencyValue == BigDecimal.ZERO &&
                value.currentInvestment == BigDecimal.ZERO &&
                value.currentProfit == BigDecimal.ZERO
        }
    }

    @Test
    fun `given no groupBy - when getting performance report - then returns ungrouped report with UNGROUPED group key`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedAmounts(UnitAmounts.EMPTY))

        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(investmentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-1000.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    vt to BigDecimal.parseString("10.00")
                )),
            ))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(currencyFilter), isNull()))
            .thenReturn(ungroupedBuckets(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("-1000.00")
                )),
            ))

        givenRate(vt, Currency.RON, "2024-01-01", "120.00")
        givenRate(vt, Currency.RON, "2024-02-01", "130.00")

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON, groupBy = null)

        assertThat(report.buckets).hasSize(2)
        assertThat(report.buckets[0].groups).hasSize(1)
        assertThat(report.buckets[0].groups[0].groupKey).isEqualTo("UNGROUPED")
        assertThat(report.buckets[0].groups[0].value.totalInvestment).isEqualTo(BigDecimal.parseString("1000.00"))
    }

    private val fund1 = uuid4().toString()
    private val fund2 = uuid4().toString()

    @Test
    fun `given two funds - when getting grouped performance report by fund - then returns per-fund metrics`(): Unit = runBlocking {
        val sxr8 = Instrument("SXR8")

        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), eq(GroupingCriteria.FUND)))
            .thenReturn(GroupedUnitAmounts(emptyMap()))

        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(investmentFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to GroupedUnitAmounts(mapOf(
                    GroupKey.ByFund(fund1) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-1000.00"))),
                    GroupKey.ByFund(fund2) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00"))),
                )),
            )))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to GroupedUnitAmounts(mapOf(
                    GroupKey.ByFund(fund1) to UnitAmounts(mapOf(vt to BigDecimal.parseString("10.00"))),
                    GroupKey.ByFund(fund2) to UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("5.00"))),
                )),
            )))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(currencyFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to GroupedUnitAmounts(mapOf(
                    GroupKey.ByFund(fund1) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-1000.00"))),
                    GroupKey.ByFund(fund2) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00"))),
                )),
            )))

        givenRate(vt, Currency.RON, "2024-01-01", "120.00")
        givenRate(vt, Currency.RON, "2024-02-01", "130.00")
        givenRate(sxr8, Currency.RON, "2024-01-01", "200.00")
        givenRate(sxr8, Currency.RON, "2024-02-01", "210.00")

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON, groupBy = GroupingCriteria.FUND)

        assertThat(report.buckets).hasSize(2)

        val jan = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(jan).hasSize(2)
        val janFund1 = jan.first { it.groupKey == fund1 }
        val janFund2 = jan.first { it.groupKey == fund2 }
        assertThat(janFund1.value.totalInvestment).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(janFund1.value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1200.00"))
        assertThat(janFund1.value.totalProfit).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(janFund2.value.totalInvestment).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(janFund2.value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(janFund2.value.totalProfit).isEqualTo(BigDecimal.parseString("500.00"))

        val feb = report.buckets[1].groups.sortedBy { it.groupKey }
        val febFund1 = feb.first { it.groupKey == fund1 }
        val febFund2 = feb.first { it.groupKey == fund2 }
        assertThat(febFund1.value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1300.00"))
        assertThat(febFund1.value.totalProfit).isEqualTo(BigDecimal.parseString("300.00"))
        assertThat(febFund1.value.currentProfit).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(febFund2.value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("1050.00"))
        assertThat(febFund2.value.totalProfit).isEqualTo(BigDecimal.parseString("550.00"))
        assertThat(febFund2.value.currentProfit).isEqualTo(BigDecimal.parseString("50.00"))
    }

    @Test
    fun `given previous balances and bucket data - when getting grouped performance report by fund - then accumulates state per group`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(investmentFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(GroupedUnitAmounts(mapOf(
                GroupKey.ByFund(fund1) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00"))),
            )))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(GroupedUnitAmounts(mapOf(
                GroupKey.ByFund(fund1) to UnitAmounts(mapOf(vt to BigDecimal.parseString("5.00"))),
            )))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(currencyFilter), eq(GroupingCriteria.FUND)))
            .thenReturn(GroupedUnitAmounts(mapOf(
                GroupKey.ByFund(fund1) to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-500.00"))),
            )))

        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), eq(GroupingCriteria.FUND)))
            .thenReturn(BucketedGroupedUnitAmounts(emptyMap()))

        givenRate(vt, Currency.RON, "2024-01-01", "110.00")
        givenRate(vt, Currency.RON, "2024-02-01", "120.00")

        val report = service.getPerformanceReport(userId, interval, targetCurrency = Currency.RON, groupBy = GroupingCriteria.FUND)

        assertThat(report.buckets).hasSize(2)
        val jan = report.buckets[0].groups
        assertThat(jan).hasSize(1)
        assertThat(jan[0].groupKey).isEqualTo(fund1)
        assertThat(jan[0].value.totalInvestment).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(jan[0].value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("550.00"))
        assertThat(jan[0].value.totalProfit).isEqualTo(BigDecimal.parseString("50.00"))

        val feb = report.buckets[1].groups
        assertThat(feb[0].value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(feb[0].value.totalProfit).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(feb[0].value.currentProfit).isEqualTo(BigDecimal.parseString("50.00"))
    }
}
