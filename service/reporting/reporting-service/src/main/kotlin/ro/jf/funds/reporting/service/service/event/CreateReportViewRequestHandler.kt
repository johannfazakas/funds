package ro.jf.funds.reporting.service.service.event

import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Handler
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.service.ReportViewTaskService

class CreateReportViewRequestHandler(
    private val reportViewTaskService: ReportViewTaskService,
) : Handler<CreateReportViewCommand> {
    override suspend fun handle(event: Event<CreateReportViewCommand>) {
        val taskId = event.correlationId ?: error("Correlation ID not found on report view task")
        reportViewTaskService.handleReportViewTask(event.userId, taskId, event.payload)
    }
}
