package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Label
import ro.jf.funds.reporting.api.model.ReportViewType
import java.util.*

data class ReportView(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val fundId: UUID,
    val type: ReportViewType,
    val labels: List<Label>,
)
