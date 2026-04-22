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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.BucketedGroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.BucketedUnitAmounts
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit

class AnalyticsServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val conversionSdk = mock<ConversionSdk>()
    private val service = AnalyticsService(analyticsRecordRepository, conversionSdk)

    private val userId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-04-01T00:00:00"),
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

    private fun unitAmounts(vararg pairs: Pair<Currency, String>) =
        UnitAmounts(pairs.associate { (unit, amount) -> unit to BigDecimal.parseString(amount) })

    @Test
    fun `given single-currency records - when getting balance report - then returns cumulative balance`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "500.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "100.00"),
                LocalDateTime.parse("2024-02-01T00:00:00") to unitAmounts(Currency.RON to "-50.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.RON to "200.00"),
            )))

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].groups).hasSize(1)
        assertThat(report.buckets[0].groups[0].groupKey).isNull()
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("550.00"))
    }

    @Test
    fun `given single-currency records - when getting net change report - then returns per-bucket net changes`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "100.00"),
                LocalDateTime.parse("2024-02-01T00:00:00") to unitAmounts(Currency.RON to "-50.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.RON to "200.00"),
            )))

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("-50.00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("200.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting balance report - then fills missing buckets with zero net change`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "500.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "100.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.RON to "200.00"),
            )))

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("600.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting net change report - then fills missing buckets with zero`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "100.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.RON to "200.00"),
            )))

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.ZERO)
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("200.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting balance report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(UnitAmounts.EMPTY)
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(emptyMap()))

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.groups.size == 1 && it.groups[0].value == BigDecimal.ZERO }
    }

    @Test
    fun `given weekly interval starting mid-week - when getting balance report - then first bucket uses actual start date`(): Unit = runBlocking {
        val midWeekInterval = ReportInterval(
            granularity = TimeGranularity.WEEKLY,
            from = LocalDateTime.parse("2024-01-03T00:00:00"),
            to = LocalDateTime.parse("2024-01-22T00:00:00"),
        )
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "1000.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-03T00:00:00") to unitAmounts(Currency.RON to "50.00"),
                LocalDateTime.parse("2024-01-08T00:00:00") to unitAmounts(Currency.RON to "100.00"),
                LocalDateTime.parse("2024-01-15T00:00:00") to unitAmounts(Currency.RON to "-30.00"),
            )))

        val report = service.getBalanceReport(userId, midWeekInterval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-03T00:00:00"))
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-01-08T00:00:00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("1050.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("1150.00"))
    }

    @Test
    fun `given monthly interval starting mid-month - when getting balance report - then first bucket uses actual start date`(): Unit = runBlocking {
        val midMonthInterval = ReportInterval(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse("2024-01-15T00:00:00"),
            to = LocalDateTime.parse("2024-04-01T00:00:00"),
        )
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "200.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-15T00:00:00") to unitAmounts(Currency.RON to "80.00"),
                LocalDateTime.parse("2024-02-01T00:00:00") to unitAmounts(Currency.RON to "-20.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.RON to "150.00"),
            )))

        val report = service.getBalanceReport(userId, midMonthInterval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("280.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("260.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting net change report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(emptyMap()))

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.groups.size == 1 && it.groups[0].value == BigDecimal.ZERO }
    }

    @Test
    fun `given multi-currency records and target currency - when getting balance report - then returns converted cumulative balance`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "1000.00", Currency.EUR to "200.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "500.00", Currency.EUR to "100.00"),
                LocalDateTime.parse("2024-02-01T00:00:00") to unitAmounts(Currency.RON to "-200.00"),
                LocalDateTime.parse("2024-03-01T00:00:00") to unitAmounts(Currency.EUR to "50.00"),
            )))
        givenRate(Currency.RON, Currency.EUR, "2024-01-01", "0.20")
        givenRate(Currency.RON, Currency.EUR, "2024-02-01", "0.20")
        givenRate(Currency.RON, Currency.EUR, "2024-03-01", "0.20")

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("400.00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("560.00"))
    }

    @Test
    fun `given multi-currency records and target currency - when getting net change report - then returns converted per-bucket values`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to unitAmounts(Currency.RON to "500.00", Currency.EUR to "100.00"),
                LocalDateTime.parse("2024-02-01T00:00:00") to unitAmounts(Currency.RON to "-200.00"),
            )))
        givenRate(Currency.RON, Currency.EUR, "2024-01-01", "0.20")
        givenRate(Currency.RON, Currency.EUR, "2024-02-01", "0.20")

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("-40.00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `given varying exchange rates - when getting balance report - then converts per-unit balances at each bucket rate`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(unitAmounts(Currency.RON to "1000.00"))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(emptyMap()))
        givenRate(Currency.RON, Currency.EUR, "2024-01-01", "0.20")
        givenRate(Currency.RON, Currency.EUR, "2024-02-01", "0.22")
        givenRate(Currency.RON, Currency.EUR, "2024-03-01", "0.25")

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("220.00"))
        assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("250.00"))
    }

    @Test
    fun `given currency grouping - when getting net change report - then returns separate groups per currency`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedGroupedUnitAmounts(any(), any(), any(), any()))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    "RON" to unitAmounts(Currency.RON to "500.00"),
                    "EUR" to unitAmounts(Currency.EUR to "100.00"),
                ),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(
                    "RON" to unitAmounts(Currency.RON to "-200.00"),
                ),
            )))
        givenRate(Currency.RON, Currency.EUR, "2024-01-01", "0.20")
        givenRate(Currency.RON, Currency.EUR, "2024-02-01", "0.20")

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.EUR, groupBy = GroupingCriteria.CURRENCY)

        assertThat(report.buckets).hasSize(3)
        val jan = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(jan).hasSize(2)
        assertThat(jan[0].groupKey).isEqualTo("EUR")
        assertThat(jan[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(jan[1].groupKey).isEqualTo("RON")
        assertThat(jan[1].value).isEqualTo(BigDecimal.parseString("100.00"))

        val feb = report.buckets[1].groups.sortedBy { it.groupKey }
        assertThat(feb).hasSize(1)
        assertThat(feb[0].groupKey).isEqualTo("RON")
        assertThat(feb[0].value).isEqualTo(BigDecimal.parseString("-40.00"))
    }

    @Test
    fun `given fund grouping - when getting balance report - then returns separate groups per fund`(): Unit = runBlocking {
        val fund1 = uuid4().toString()
        val fund2 = uuid4().toString()
        whenever(analyticsRecordRepository.getGroupedUnitAmountsBefore(any(), any(), any(), any()))
            .thenReturn(GroupedUnitAmounts(mapOf(
                fund1 to unitAmounts(Currency.RON to "300.00"),
                fund2 to unitAmounts(Currency.RON to "200.00"),
            )))
        whenever(analyticsRecordRepository.getBucketedGroupedUnitAmounts(any(), any(), any(), any()))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    fund1 to unitAmounts(Currency.RON to "100.00"),
                    fund2 to unitAmounts(Currency.RON to "50.00"),
                ),
            )))

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON, groupBy = GroupingCriteria.FUND)

        assertThat(report.buckets).hasSize(3)
        val jan = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(jan).hasSize(2)
        assertThat(jan[0].groupKey).isEqualTo(fund1.coerceAtMost(fund2))
        assertThat(jan[1].groupKey).isEqualTo(fund1.coerceAtLeast(fund2))

        val feb = report.buckets[1].groups.sortedBy { it.groupKey }
        assertThat(feb).hasSize(2)
        val fund1Feb = feb.first { it.groupKey == fund1 }
        val fund2Feb = feb.first { it.groupKey == fund2 }
        assertThat(fund1Feb.value).isEqualTo(BigDecimal.parseString("400.00"))
        assertThat(fund2Feb.value).isEqualTo(BigDecimal.parseString("250.00"))
    }

    @Test
    fun `given category grouping - when getting net change report - then returns separate groups per category`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBucketedGroupedUnitAmounts(any(), any(), any(), any()))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    "food" to unitAmounts(Currency.RON to "300.00"),
                    "transport" to unitAmounts(Currency.RON to "150.00"),
                ),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(
                    "food" to unitAmounts(Currency.RON to "250.00"),
                ),
            )))

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON, groupBy = GroupingCriteria.CATEGORY)

        assertThat(report.buckets).hasSize(3)
        val jan = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(jan).hasSize(2)
        assertThat(jan[0].groupKey).isEqualTo("food")
        assertThat(jan[0].value).isEqualTo(BigDecimal.parseString("300.00"))
        assertThat(jan[1].groupKey).isEqualTo("transport")
        assertThat(jan[1].value).isEqualTo(BigDecimal.parseString("150.00"))

        val feb = report.buckets[1].groups
        assertThat(feb).hasSize(1)
        assertThat(feb[0].groupKey).isEqualTo("food")
        assertThat(feb[0].value).isEqualTo(BigDecimal.parseString("250.00"))
    }

    @Test
    fun `given category grouping - when getting balance report - then returns cumulative balance per category`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getGroupedUnitAmountsBefore(any(), any(), any(), any()))
            .thenReturn(GroupedUnitAmounts(mapOf(
                "food" to unitAmounts(Currency.RON to "500.00"),
                "transport" to unitAmounts(Currency.RON to "200.00"),
            )))
        whenever(analyticsRecordRepository.getBucketedGroupedUnitAmounts(any(), any(), any(), any()))
            .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    "food" to unitAmounts(Currency.RON to "100.00"),
                ),
            )))

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON, groupBy = GroupingCriteria.CATEGORY)

        assertThat(report.buckets).hasSize(3)
        val jan = report.buckets[0].groups.sortedBy { it.groupKey }
        assertThat(jan).hasSize(2)
        assertThat(jan.first { it.groupKey == "food" }.value).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(jan.first { it.groupKey == "transport" }.value).isEqualTo(BigDecimal.parseString("200.00"))

        val feb = report.buckets[1].groups.sortedBy { it.groupKey }
        assertThat(feb.first { it.groupKey == "food" }.value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(feb.first { it.groupKey == "transport" }.value).isEqualTo(BigDecimal.parseString("200.00"))
    }
}
