package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.util.UUID.randomUUID

class ReportViewServiceTest {

    private val reportViewRepository = mock<ReportViewRepository>()

    private val reportViewService = ReportViewService(reportViewRepository)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val fundId = randomUUID()

    @Test
    fun `create report view`(): Unit = runBlocking {
        val request = CreateReportViewTO(reportViewName, fundId, ReportViewType.EXPENSE)
        whenever(reportViewRepository.create(userId, reportViewName, fundId, ReportViewType.EXPENSE))
            .thenReturn(
                ReportView(reportViewId, userId, reportViewName, fundId, ReportViewType.EXPENSE)
            )

        val reportView = reportViewService.createReportView(userId, request)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(fundId)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)

        verify(reportViewRepository, times(1))
            .create(userId, reportViewName, fundId, ReportViewType.EXPENSE)
    }

    @Test
    fun `get report view data`() {

    }
}
