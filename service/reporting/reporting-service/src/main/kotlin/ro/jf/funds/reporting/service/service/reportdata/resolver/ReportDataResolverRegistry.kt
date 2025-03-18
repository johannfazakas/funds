package ro.jf.funds.reporting.service.service.reportdata.resolver

class ReportDataResolverRegistry(
    val net: NetDataResolver = NetDataResolver(),
    val valueReport: ValueReportDataResolver = ValueReportDataResolver(),
    val groupedNet: GroupedNetDataResolver = GroupedNetDataResolver(),
    val groupedBudget: GroupedBudgetDataResolver = GroupedBudgetDataResolver(),
)
