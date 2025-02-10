package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.api.model.TimeGranularity
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ReportDataServiceTest {
    private val reportViewRepository = mock<ReportViewRepository>()
    private val reportRecordRepository = mock<ReportRecordRepository>()
    private val reportDataService = ReportDataService(reportViewRepository, reportRecordRepository)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val allLabels = labelsOf("need", "want")

    @Test
    fun `get expense report view data grouped by months`(): Unit = runBlocking {
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )
        val interval = DateInterval(from = LocalDate.parse("2021-09-03"), to = LocalDate.parse("2021-11-25"))
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    reportRecord(
                        LocalDate.parse("2021-09-03"), RON, BigDecimal("-100.0"), BigDecimal("-100.0"), labelsOf("need")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-09-15"), EUR, BigDecimal("-40.0"), BigDecimal("-200.0"), labelsOf("want")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-10-07"), RON, BigDecimal("-30.0"), BigDecimal("-30.0"), labelsOf("want")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-10-08"), RON, BigDecimal("-16.0"), BigDecimal("-16.0"), labelsOf("other")
                    ),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].timeBucket).isEqualTo(LocalDate.parse("2021-09-01"))
        assertThat(data.data[0].amount).isEqualByComparingTo(BigDecimal("-300.0"))
        assertThat(data.data[1].timeBucket).isEqualTo(LocalDate.parse("2021-10-01"))
        assertThat(data.data[1].amount).isEqualByComparingTo(BigDecimal("-30.0"))
        assertThat(data.data[2].timeBucket).isEqualTo(LocalDate.parse("2021-11-01"))
        assertThat(data.data[2].amount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `get value data with single currency`(): Unit = runBlocking {
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )
        val to = LocalDate.parse("2021-11-25")
        val interval = DateInterval(from = LocalDate.parse("2021-09-02"), to = to)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, to))
            .thenReturn(
                listOf(
                    reportRecord(
                        LocalDate.parse("2021-08-02"), RON, BigDecimal("100.0"), BigDecimal("100.0"), labelsOf("need")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-09-02"), RON, BigDecimal("200.0"), BigDecimal("200.0"), labelsOf("need")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-09-03"), RON, BigDecimal("-100.0"), BigDecimal("-100.0"), labelsOf("need")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-09-15"), RON, BigDecimal("-40.0"), BigDecimal("-40.0"), labelsOf("want")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-10-07"), RON, BigDecimal("400.0"), BigDecimal("400.0"), labelsOf("want")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-10-07"), RON, BigDecimal("-30.0"), BigDecimal("-30.0"), labelsOf("want")
                    ),
                    reportRecord(
                        LocalDate.parse("2021-10-08"), RON, BigDecimal("-16.0"), BigDecimal("-16.0"), labelsOf("other")
                    ),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].value.start)
            .isEqualByComparingTo(BigDecimal("100.0"))
        assertThat(data.data[0].value.end)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.data[1].value.start)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.data[1].value.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.data[2].value.start)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.data[2].value.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
    }

    private fun reportRecord(
        date: LocalDate, unit: FinancialUnit, amount: BigDecimal, reportCurrencyAmount: BigDecimal, labels: List<Label>,
    ) =
        ReportRecord(randomUUID(), userId, reportViewId, date, unit, amount, reportCurrencyAmount, labels)
}
