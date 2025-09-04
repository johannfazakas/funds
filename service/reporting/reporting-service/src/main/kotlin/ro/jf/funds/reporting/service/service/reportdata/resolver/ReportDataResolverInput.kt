package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ReportTransactionStore
import ro.jf.funds.reporting.service.domain.ReportDataInterval
import ro.jf.funds.reporting.service.domain.ReportView

data class ReportDataResolverInput(
    val reportView: ReportView,
    val interval: ReportDataInterval,
    val reportTransactionStore: ReportTransactionStore,
) {
    val dataConfiguration = reportView.dataConfiguration
    val userId = reportView.userId
}
