package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.AnalyticsValueAggregate
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import com.benasher44.uuid.uuid4

class AnalyticsServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val service = AnalyticsService(analyticsRecordRepository)

    private val userId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-04-01T00:00:00"),
    )

    @Test
    fun `given baseline and monthly aggregates - when getting balance report - then returns cumulative balance`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("500.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-02-01T00:00:00"), BigDecimal.parseString("-50.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("200.00")),
                )
            )

        val report = service.getBalanceReport(userId, interval)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("550.00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("750.00"))
    }

    @Test
    fun `given baseline and monthly aggregates - when getting net change report - then returns per-bucket net changes`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("500.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-02-01T00:00:00"), BigDecimal.parseString("-50.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("200.00")),
                )
            )

        val report = service.getNetChangeReport(userId, interval)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("-50.00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("200.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting balance report - then fills missing buckets with zero net change`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("500.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("200.00")),
                )
            )

        val report = service.getBalanceReport(userId, interval)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("800.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting net change report - then fills missing buckets with zero`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("500.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("200.00")),
                )
            )

        val report = service.getNetChangeReport(userId, interval)

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
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO)
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())

        val report = service.getBalanceReport(userId, interval)

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
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("1000.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("50.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-08T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-15T00:00:00"), BigDecimal.parseString("-30.00")),
                )
            )

        val report = service.getBalanceReport(userId, midWeekInterval)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-03T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("1050.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-01-08T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("1150.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("1120.00"))
    }

    @Test
    fun `given monthly interval starting mid-month - when getting balance report - then first bucket uses actual start date`(): Unit = runBlocking {
        val midMonthInterval = ReportInterval(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse("2024-01-15T00:00:00"),
            to = LocalDateTime.parse("2024-04-01T00:00:00"),
        )
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("200.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("80.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-02-01T00:00:00"), BigDecimal.parseString("-20.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("150.00")),
                )
            )

        val report = service.getBalanceReport(userId, midMonthInterval)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-15T00:00:00"))
        assertThat(report.buckets[0].value).isEqualTo(BigDecimal.parseString("280.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].value).isEqualTo(BigDecimal.parseString("260.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].value).isEqualTo(BigDecimal.parseString("410.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting net change report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO)
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())

        val report = service.getNetChangeReport(userId, interval)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.value == BigDecimal.ZERO }
    }
}
