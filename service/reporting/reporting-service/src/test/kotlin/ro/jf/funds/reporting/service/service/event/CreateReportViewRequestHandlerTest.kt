package ro.jf.funds.reporting.service.service.event

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.service.domain.ReportViewTask
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.ReportViewTaskService
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class CreateReportViewRequestHandlerTest {

    private val reportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val reportViewTaskRepository = ReportViewTaskRepository(PostgresContainerExtension.connection)

    private val reportViewService = ReportViewService(reportViewRepository)
    private val reportViewTaskService = ReportViewTaskService(reportViewService, reportViewTaskRepository, mock())

    private val requestHandler = CreateReportViewRequestHandler(reportViewTaskService)

    private val userId = randomUUID()
    private val viewName = "Expenses"
    private val fundId = randomUUID()

    @Test
    fun `handle create report view request`(): Unit = runBlocking {
        val initialTask = reportViewTaskRepository.create(userId)
        val payload = CreateReportViewTO(viewName, fundId, ReportViewType.EXPENSE)
        val event = Event(userId, payload, initialTask.taskId)

        requestHandler.handle(event)

        val completedTask = reportViewTaskRepository.findById(userId, initialTask.taskId) ?: error("Task not found")
        assertThat(completedTask).isInstanceOf(ReportViewTask.Completed::class.java)
        completedTask as ReportViewTask.Completed
        val reportViewId = completedTask.reportViewId

        val reportView = reportViewRepository.findById(userId, reportViewId) ?: error("Report view not found")
        assertThat(reportView.fundId).isEqualTo(fundId)
        assertThat(reportView.name).isEqualTo(viewName)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)
    }
}
