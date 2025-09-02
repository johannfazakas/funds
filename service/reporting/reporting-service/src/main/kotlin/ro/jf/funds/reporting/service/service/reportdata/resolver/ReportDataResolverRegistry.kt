package ro.jf.funds.reporting.service.service.reportdata.resolver

class ReportDataResolverRegistry(
    val net: NetDataResolver,
    val valueReport: ValueReportDataResolver,
    val groupedNet: GroupedNetDataResolver,
    val groupedBudget: GroupedBudgetDataResolver,
    val performanceReport: PerformanceReportDataResolver,
)
