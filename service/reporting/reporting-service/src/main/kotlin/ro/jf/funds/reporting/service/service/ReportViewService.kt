package ro.jf.funds.reporting.service.service

import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTypeTO
import java.util.*
import java.util.UUID.randomUUID

class ReportViewService {
    suspend fun createReportViewTask(userId: UUID, request: CreateReportViewTO): ReportViewTaskTO {
        return ReportViewTaskTO.Completed(
            taskId = randomUUID(),
            report = ReportViewTO(
                name = request.name,
                fundId = request.fundId,
                type = request.type
            )
        )
    }

    suspend fun getReportViewTask(userId: UUID, taskId: UUID): ReportViewTaskTO {
        return ReportViewTaskTO.Completed(
            taskId = taskId,
            report = ReportViewTO(
                name = "Report",
                fundId = randomUUID(),
                type = ReportViewTypeTO.EXPENSE
            )
        )
    }

    fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO {
        TODO("Not yet implemented")
    }
}
