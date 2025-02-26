package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import java.util.*

// TODO(Johann) why do I have to have a predefined report view? Can't I just import everything?
data class ReportView(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val fundId: UUID,
    // TODO(Johann-11) remove report view type from db
//    val type: ReportViewType,
    // TODO(Johann) why do I have to have a predefined currency?
    val currency: Currency,
    val labels: List<Label>,
//    val configuration: ReportViewConfiguration,
)

data class ReportViewConfiguration(
    val currency: Currency,
)
