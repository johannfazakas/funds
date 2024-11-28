package ro.jf.funds.reporting.service.service.event

import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Handler
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.service.service.ReportViewService

class CreateReportViewRequestHandler(
    private val reportViewService: ReportViewService,
) : Handler<CreateReportViewTO> {
    override suspend fun handle(event: Event<CreateReportViewTO>) {
        reportViewService.createReportView(event.userId, event.payload)
    }
}
