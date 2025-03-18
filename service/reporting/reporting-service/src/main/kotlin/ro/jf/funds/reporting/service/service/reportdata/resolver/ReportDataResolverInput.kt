package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.domain.RecordCatalog
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration

data class ReportDataResolverInput(
    val dateInterval: GranularDateInterval,
    val catalog: RecordCatalog,
    val conversions: ConversionsResponse,
    val dataConfiguration: ReportDataConfiguration,
)
