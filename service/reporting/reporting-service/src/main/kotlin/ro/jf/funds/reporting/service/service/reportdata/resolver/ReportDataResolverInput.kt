package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.service.domain.RecordCatalog
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration
import ro.jf.funds.reporting.service.domain.ReportDataInterval

data class ReportDataResolverInput(
    val interval: ReportDataInterval,
    val catalog: RecordCatalog,
    val conversions: ConversionsResponse,
    val dataConfiguration: ReportDataConfiguration,
)
