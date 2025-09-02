package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.RecordStore
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration
import ro.jf.funds.reporting.service.domain.ReportDataInterval
import java.util.*

data class ReportDataResolverInput(
    // TODO(Johann) maybe the complete reportview might be passed
    val userId: UUID,
    val interval: ReportDataInterval,
    val recordStore: RecordStore,
    val dataConfiguration: ReportDataConfiguration,
)
