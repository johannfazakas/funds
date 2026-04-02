package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.BucketedUnitAggregates
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import com.benasher44.uuid.uuid4

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

    @Test
    fun `given single-currency records - when getting balance report - then returns cumulative balance`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(mapOf(Currency.RON to BigDecimal.parseString("500.00")))
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("100.00")),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("-50.00")),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("200.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("550.00"))
    }

    @Test
    fun `given single-currency records - when getting net change report - then returns per-bucket net changes`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("100.00")),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("-50.00")),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("200.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("-50.00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("200.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting balance report - then fills missing buckets with zero net change`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(mapOf(Currency.RON to BigDecimal.parseString("500.00")))
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("100.00")),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("200.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("600.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting net change report - then fills missing buckets with zero`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("100.00")),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("200.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.ZERO)
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("200.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting balance report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(emptyMap())
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(emptyMap()))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.value == BigDecimal.ZERO }
    }

    @Test
    fun `given weekly interval starting mid-week - when getting balance report - then first bucket uses actual start date`(): Unit = runBlocking {
        val midWeekInterval = ReportInterval(
            granularity = TimeGranularity.WEEKLY,
            from = LocalDateTime.parse("2024-01-03T00:00:00"),
            to = LocalDateTime.parse("2024-01-22T00:00:00"),
        )
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(mapOf(Currency.RON to BigDecimal.parseString("1000.00")))
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-03T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("50.00")),
                LocalDateTime.parse("2024-01-08T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("100.00")),
                LocalDateTime.parse("2024-01-15T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("-30.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getBalanceReport(userId, midWeekInterval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-03T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("1000.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-01-08T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("1050.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("1150.00"))
    }

    @Test
    fun `given monthly interval starting mid-month - when getting balance report - then first bucket uses actual start date`(): Unit = runBlocking {
        val midMonthInterval = ReportInterval(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse("2024-01-15T00:00:00"),
            to = LocalDateTime.parse("2024-04-01T00:00:00"),
        )
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(mapOf(Currency.RON to BigDecimal.parseString("200.00")))
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-15T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("80.00")),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("-20.00")),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(Currency.RON to BigDecimal.parseString("150.00")),
            )))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getBalanceReport(userId, midMonthInterval, targetCurrency = Currency.RON)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("280.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("260.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting net change report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(emptyMap()))
        whenever(conversionSdk.convert(any())).thenReturn(ConversionsResponse.empty())

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.RON)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.value == BigDecimal.ZERO }
    }

    @Test
    fun `given multi-currency records and target currency - when getting balance report - then returns converted cumulative balance`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(
                mapOf(
                    Currency.RON to BigDecimal.parseString("1000.00"),
                    Currency.EUR to BigDecimal.parseString("200.00"),
                )
            )
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    Currency.RON to BigDecimal.parseString("500.00"),
                    Currency.EUR to BigDecimal.parseString("100.00"),
                ),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(
                    Currency.RON to BigDecimal.parseString("-200.00"),
                ),
                LocalDateTime.parse("2024-03-01T00:00:00") to mapOf(
                    Currency.EUR to BigDecimal.parseString("50.00"),
                ),
            )))
        whenever(conversionSdk.convert(any()))
            .thenReturn(
                ConversionsResponse(
                    listOf(
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-01-01"), BigDecimal.parseString("0.20")),
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-02-01"), BigDecimal.parseString("0.20")),
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-03-01"), BigDecimal.parseString("0.20")),
                    )
                )
            )

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        // Jan: baseline RON 1000, EUR 200 -> 1000*0.20 + 200 = 400
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("400.00"))
        // Feb: RON 1500, EUR 300 (after Jan changes) -> 1500*0.20 + 300 = 600
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("600.00"))
        // Mar: RON 1300, EUR 300 (after Feb changes) -> 1300*0.20 + 300 = 560
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("560.00"))
    }

    @Test
    fun `given multi-currency records and target currency - when getting net change report - then returns converted per-bucket values`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to mapOf(
                    Currency.RON to BigDecimal.parseString("500.00"),
                    Currency.EUR to BigDecimal.parseString("100.00"),
                ),
                LocalDateTime.parse("2024-02-01T00:00:00") to mapOf(
                    Currency.RON to BigDecimal.parseString("-200.00"),
                ),
            )))
        whenever(conversionSdk.convert(any()))
            .thenReturn(
                ConversionsResponse(
                    listOf(
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-01-01"), BigDecimal.parseString("0.20")),
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-02-01"), BigDecimal.parseString("0.20")),
                    )
                )
            )

        val report = service.getNetChangeReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        // Jan: 500 RON * 0.20 + 100 EUR = 200 EUR
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        // Feb: -200 RON * 0.20 = -40 EUR
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("-40.00"))
        // Mar: no data = 0
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `given varying exchange rates - when getting balance report - then converts per-unit balances at each bucket rate`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getBalanceBefore(any(), any(), any()))
            .thenReturn(mapOf(Currency.RON to BigDecimal.parseString("1000.00")))
        whenever(analyticsRecordRepository.getValueAggregatesByUnit(any(), any(), any()))
            .thenReturn(BucketedUnitAggregates(emptyMap()))
        whenever(conversionSdk.convert(any()))
            .thenReturn(
                ConversionsResponse(
                    listOf(
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-01-01"), BigDecimal.parseString("0.20")),
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-02-01"), BigDecimal.parseString("0.22")),
                        ConversionResponse(Currency.RON, Currency.EUR, LocalDate.parse("2024-03-01"), BigDecimal.parseString("0.25")),
                    )
                )
            )

        val report = service.getBalanceReport(userId, interval, targetCurrency = Currency.EUR)

        assertThat(report.buckets).hasSize(3)
        // RON balance stays 1000, converted at each bucket's rate
        // Jan: 1000 * 0.20 = 200
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("200.00"))
        // Feb: 1000 * 0.22 = 220
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("220.00"))
        // Mar: 1000 * 0.25 = 250
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("250.00"))
    }
}
