package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label

@Serializable
data class ReportDataConfigurationTO(
    val currency: Currency,
    val groups: List<ReportGroupTO>? = null,
    val reports: ReportsConfigurationTO,
    val forecast: ForecastConfigurationTO = ForecastConfigurationTO(1),
)

@Serializable
data class ReportGroupTO(
    val name: String,
    val filter: RecordFilterTO,
)

@Serializable
data class RecordFilterTO(
    val labels: List<Label>,
) {
    companion object {
        fun byLabels(vararg labels: String): RecordFilterTO = RecordFilterTO(labels.map(::Label))
    }
}

@Serializable
data class ReportsConfigurationTO(
    val net: NetReportConfigurationTO = NetReportConfigurationTO(enabled = false),
    val valueReport: ValueReportConfigurationTO = ValueReportConfigurationTO(false),
    val groupedNet: GenericReportConfigurationTO = GenericReportConfigurationTO(false),
    val groupedBudget: GroupedBudgetReportConfigurationTO = GroupedBudgetReportConfigurationTO(false, listOf()),
    val performanceReport: PerformanceReportConfigurationTO = PerformanceReportConfigurationTO(false),
    val unitPerformanceReport: PerformanceReportConfigurationTO = PerformanceReportConfigurationTO(false),
)

@Serializable
data class GenericReportConfigurationTO(
    val enabled: Boolean,
)

@Serializable
data class NetReportConfigurationTO(
    val enabled: Boolean,
    val filter: RecordFilterTO? = null,
)

@Serializable
data class ValueReportConfigurationTO(
    val enabled: Boolean,
    val filter: RecordFilterTO? = null,
)

@Serializable
data class GroupedBudgetReportConfigurationTO(
    val enabled: Boolean,
    val distributions: List<BudgetDistributionTO>,
) {
    @Serializable
    data class BudgetDistributionTO(
        val default: Boolean,
        val from: YearMonthTO?,
        val groups: List<GroupBudgetPercentageTO>,
    )

    @Serializable
    data class GroupBudgetPercentageTO(
        val group: String,
        val percentage: Int,
    )
}

@Serializable
data class PerformanceReportConfigurationTO(
    val enabled: Boolean,
)

@Serializable
data class ForecastConfigurationTO(
    val inputBuckets: Int,
) {
    init {
        require(inputBuckets > 0) { "Input buckets must be greater than 0" }
    }
}
