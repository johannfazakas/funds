package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.CreateReportRecordCommand
import ro.jf.funds.reporting.service.domain.ExpenseReportDataBucket
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ReportViewServiceTest {

    private val reportViewRepository = mock<ReportViewRepository>()
    private val reportRecordRepository = mock<ReportRecordRepository>()
    private val fundTransactionSdk = mock<FundTransactionSdk>()

    private val reportViewService = ReportViewService(reportViewRepository, reportRecordRepository, fundTransactionSdk)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val bankAccountId = randomUUID()
    private val cashAccountId = randomUUID()
    private val dateTime1 = LocalDateTime.parse("2021-09-01T12:00:00")
    private val dateTime2 = LocalDateTime.parse("2021-09-03T12:00:00")

    @Test
    fun `create report view should create report view`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE)
        whenever(reportViewRepository.create(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE))
            .thenReturn(
                ReportView(reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE)
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of())

        val reportView = reportViewService.createReportView(userId, request)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(expensesFundId)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)

        verify(reportViewRepository, times(1))
            .create(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE)
    }

    @Test
    fun `create report view should store single fund report records`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE)
        whenever(reportViewRepository.create(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE))
            .thenReturn(ReportView(reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE))

        val transaction1 =
            transaction(userId, dateTime1, listOf(record(expensesFundId, bankAccountId, BigDecimal("100.0"))))
        val transaction2 =
            transaction(userId, dateTime2, listOf(record(expensesFundId, cashAccountId, BigDecimal("-200.0"))))
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId))
            .thenReturn(ListTO.of(transaction1, transaction2))

        val reportView = reportViewService.createReportView(userId, request)

        val commandCaptor = argumentCaptor<CreateReportRecordCommand>()
        verify(reportRecordRepository, times(2)).create(commandCaptor.capture())
        assertThat(commandCaptor.allValues).containsExactlyInAnyOrder(
            CreateReportRecordCommand(userId, reportView.id, dateTime1.date, BigDecimal("100.0")),
            CreateReportRecordCommand(userId, reportView.id, dateTime2.date, BigDecimal("-200.0"))
        )
    }

    @Test
    fun `get expense report view data grouped by months`(): Unit = runBlocking {
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(ReportView(reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE))
        val interval = DateInterval(from = LocalDate.parse("2021-09-03"), to = LocalDate.parse("2021-11-25"))
        whenever(reportRecordRepository.findByViewInInterval(userId, reportViewId, interval))
            .thenReturn(
                listOf(
                    reportRecord(LocalDate.parse("2021-09-03"), BigDecimal("-100.0")),
                    reportRecord(LocalDate.parse("2021-09-15"), BigDecimal("-40.0")),
                    reportRecord(LocalDate.parse("2021-10-07"), BigDecimal("-30.0")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportViewService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data).containsExactly(
            ExpenseReportDataBucket(LocalDate.parse("2021-09-01"), BigDecimal("-140.0")),
            ExpenseReportDataBucket(LocalDate.parse("2021-10-01"), BigDecimal("-30.0")),
            ExpenseReportDataBucket(LocalDate.parse("2021-11-01"), BigDecimal.ZERO),
        )
    }

    private fun reportRecord(date: LocalDate, amount: BigDecimal) =
        ReportRecord(randomUUID(), userId, reportViewId, date, amount)
}
