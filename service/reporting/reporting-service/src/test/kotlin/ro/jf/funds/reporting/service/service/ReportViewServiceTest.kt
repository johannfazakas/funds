package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*
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
    private val allLabels = labelsOf("need", "want")

    @Test
    fun `create report view should create report view`(): Unit = runBlocking {
        val request =
            CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels)
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(
            reportViewRepository.create(
                userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
            )
        )
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of())

        val reportView = reportViewService.createReportView(userId, request)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(expensesFundId)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)

        verify(reportViewRepository, times(1))
            .create(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels)
    }

    @Test
    fun `create report view should store single fund report records`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels)
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(
            reportViewRepository.create(
                userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
            )
        )
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )

        val transaction1 =
            transaction(
                userId, dateTime1, listOf(
                    record(expensesFundId, bankAccountId, BigDecimal("100.0"), RON, labelsOf("need"))
                )
            )
        val transaction2 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-200.0"), RON, labelsOf("want"))
                )
            )
        val transaction3 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-20.0"), RON, labelsOf("other"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId))
            .thenReturn(ListTO.of(transaction1, transaction2, transaction3))

        val reportView = reportViewService.createReportView(userId, request)

        val commandCaptor = argumentCaptor<CreateReportRecordCommand>()
        verify(reportRecordRepository, times(3)).create(commandCaptor.capture())
        assertThat(commandCaptor.allValues).containsExactlyInAnyOrder(
            CreateReportRecordCommand(userId, reportView.id, dateTime1.date, BigDecimal("100.0"), labelsOf("need")),
            CreateReportRecordCommand(userId, reportView.id, dateTime2.date, BigDecimal("-200.0"), labelsOf("want")),
            CreateReportRecordCommand(userId, reportView.id, dateTime2.date, BigDecimal("-20.0"), labelsOf("other"))
        )
    }

    @Test
    fun `create report view with same name should raise error`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels)
        whenever(reportViewRepository.findByName(userId, reportViewName))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, request) } }
            .isInstanceOf(ReportingException.ReportViewAlreadyExists::class.java)

        verify(reportViewRepository, never()).create(
            userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
        )
    }

    @Test
    fun `get expense report view data grouped by months`(): Unit = runBlocking {
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )
        val interval = DateInterval(from = LocalDate.parse("2021-09-03"), to = LocalDate.parse("2021-11-25"))
        whenever(reportRecordRepository.findByViewInInterval(userId, reportViewId, interval))
            .thenReturn(
                listOf(
                    reportRecord(LocalDate.parse("2021-09-03"), BigDecimal("-100.0"), labelsOf("need")),
                    reportRecord(LocalDate.parse("2021-09-15"), BigDecimal("-40.0"), labelsOf("want")),
                    reportRecord(LocalDate.parse("2021-10-07"), BigDecimal("-30.0"), labelsOf("want")),
                    reportRecord(LocalDate.parse("2021-10-08"), BigDecimal("-16.0"), labelsOf("other")),
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

    private fun reportRecord(date: LocalDate, amount: BigDecimal, labels: List<Label>) =
        ReportRecord(randomUUID(), userId, reportViewId, date, amount, labels)
}
