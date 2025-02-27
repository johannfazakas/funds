package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import java.util.*

// TODO(Johann) why do I have to have a predefined report view? Can't I just import everything?
data class ReportView(
    val id: UUID,
    val userId: UUID,
    val name: String,
    // TODO(Johann-11) thinking about fundId, isn't this a data configuration?
    val fundId: UUID,
    val dataConfiguration: ReportDataConfiguration,
    // TODO(Johann-11) remove report view type from db
//    val type: ReportViewType,
    // TODO(Johann-11) remove currency and labels
    val currency: Currency = dataConfiguration.currency,
    val labels: List<Label> = dataConfiguration.filter.labels ?: emptyList(),
)
