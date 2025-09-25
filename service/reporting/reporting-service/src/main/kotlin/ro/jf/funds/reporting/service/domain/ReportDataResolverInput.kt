package ro.jf.funds.reporting.service.domain

data class ReportDataResolverInput(
    val reportView: ReportView,
    val interval: ReportDataInterval,
    val reportTransactionStore: ReportTransactionStore,
) {
    val dataConfiguration = reportView.dataConfiguration
    val userId = reportView.userId
}