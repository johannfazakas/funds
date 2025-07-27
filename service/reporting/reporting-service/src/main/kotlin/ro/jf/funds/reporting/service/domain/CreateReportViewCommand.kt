package ro.jf.funds.reporting.service.domain

import java.util.*

data class CreateReportViewCommand(
    val userId: UUID,
    val name: String,
    val fundId: UUID,
    val dataConfiguration: ReportDataConfiguration,
)
