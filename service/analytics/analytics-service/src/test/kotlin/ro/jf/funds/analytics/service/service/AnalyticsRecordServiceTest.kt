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
import ro.jf.funds.analytics.service.domain.AnalyticsValueAggregate
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import com.benasher44.uuid.uuid4

class AnalyticsRecordServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val service = AnalyticsRecordService(analyticsRecordRepository)

    private val userId = uuid4()
    private val from = LocalDateTime.parse("2024-01-01T00:00:00")
    private val to = LocalDateTime.parse("2024-04-01T00:00:00")

    @Test
    fun `given baseline and monthly aggregates - when getting value report - then returns cumulative values`(): Unit = runBlocking {
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

        val report = service.getValueReport(userId, TimeGranularity.MONTHLY, from, to)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].netChange).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[0].balance).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[1].netChange).isEqualTo(BigDecimal.parseString("-50.00"))
        assertThat(report.buckets[1].balance).isEqualTo(BigDecimal.parseString("550.00"))
        assertThat(report.buckets[2].netChange).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[2].balance).isEqualTo(BigDecimal.parseString("750.00"))
    }

    @Test
    fun `given gap in monthly aggregates - when getting value report - then fills missing buckets with zero`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.parseString("500.00"))
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-01-01T00:00:00"), BigDecimal.parseString("100.00")),
                    AnalyticsValueAggregate(LocalDateTime.parse("2024-03-01T00:00:00"), BigDecimal.parseString("200.00")),
                )
            )

        val report = service.getValueReport(userId, TimeGranularity.MONTHLY, from, to)

        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
        assertThat(report.buckets[0].netChange).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[0].balance).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
        assertThat(report.buckets[1].netChange).isEqualTo(BigDecimal.ZERO)
        assertThat(report.buckets[1].balance).isEqualTo(BigDecimal.parseString("600.00"))
        assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
        assertThat(report.buckets[2].netChange).isEqualTo(BigDecimal.parseString("200.00"))
        assertThat(report.buckets[2].balance).isEqualTo(BigDecimal.parseString("800.00"))
    }

    @Test
    fun `given no baseline and no aggregates - when getting value report - then returns zero-filled buckets`(): Unit = runBlocking {
        whenever(analyticsRecordRepository.getSumBefore(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO)
        whenever(analyticsRecordRepository.getValueAggregates(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())

        val report = service.getValueReport(userId, TimeGranularity.MONTHLY, from, to)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(3)
        assertThat(report.buckets).allMatch { it.netChange == BigDecimal.ZERO && it.balance == BigDecimal.ZERO }
    }
}
