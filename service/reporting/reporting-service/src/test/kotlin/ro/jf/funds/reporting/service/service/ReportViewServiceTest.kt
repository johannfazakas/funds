package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewType
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
    private val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")

    @Test
    fun `create report view`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE)
        whenever(reportViewRepository.create(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE))
            .thenReturn(
                ReportView(reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE)
            )
        whenever(reportRecordRepository.create(userId, reportViewId, dateTime.date, BigDecimal("100.0")))
            .thenReturn(
                ReportRecord(
                    randomUUID(),
                    userId,
                    reportViewId,
                    dateTime.date,
                    BigDecimal("100.0")
                )
            )

        val transaction =
            transaction(userId, dateTime, listOf(record(expensesFundId, bankAccountId, BigDecimal("100.0"))))
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of(transaction))

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
    fun `get report view data`() {

    }
}
