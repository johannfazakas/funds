package ro.jf.funds.reporting.service.domain

import java.util.*

data class ReportView(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val fundId: UUID,
    val dataConfiguration: ReportDataConfiguration,
)
