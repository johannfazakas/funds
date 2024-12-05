package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportViewTask
import java.util.*

suspend fun ReportViewTask.toTO(reportViewSupplier: suspend (UUID) -> ReportView): ReportViewTaskTO = when (this) {
    is ReportViewTask.InProgress -> ReportViewTaskTO.InProgress(taskId)
    is ReportViewTask.Completed -> ReportViewTaskTO.Completed(taskId, reportViewSupplier(reportViewId).toTO())
    is ReportViewTask.Failed -> ReportViewTaskTO.Failed(taskId, reason)
}
