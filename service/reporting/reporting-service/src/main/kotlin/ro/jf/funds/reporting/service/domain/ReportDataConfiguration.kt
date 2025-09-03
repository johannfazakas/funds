package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label

@Serializable
data class ReportDataConfiguration(
    val currency: Currency,
    val groups: List<ReportGroup>? = null,
    val reports: ReportsConfiguration,
    val forecast: ForecastConfiguration = ForecastConfiguration(1),
)

@Serializable
data class ReportGroup(
    val name: String,
    val filter: RecordFilter,
)

@Serializable
data class RecordFilter(
    val labels: List<Label>,
) {
    companion object {
        fun byLabels(vararg labels: String): RecordFilter = RecordFilter(labels.map(::Label))
    }

    fun test(reportRecord: ReportRecord): Boolean = labels.any { reportRecord.labels.contains(it) }
}

@Serializable
data class ReportsConfiguration(
    val net: NetReportConfiguration = NetReportConfiguration(enabled = false),
    val valueReport: ValueReportConfiguration = ValueReportConfiguration(enabled = false),
    val groupedNet: GenericReportConfiguration = GenericReportConfiguration(enabled = false),
    val groupedBudget: GroupedBudgetReportConfiguration = GroupedBudgetReportConfiguration(enabled = false, listOf()),
    val performance: PerformanceReportConfiguration = PerformanceReportConfiguration(enabled = false),
) {
    fun withNet(enabled: Boolean, filter: RecordFilter? = null) = copy(net = NetReportConfiguration(enabled, filter))
    fun withGroupedNet(enabled: Boolean) = copy(groupedNet = GenericReportConfiguration(enabled))
    fun withValueReport(enabled: Boolean, filter: RecordFilter? = null) =
        copy(valueReport = ValueReportConfiguration(enabled, filter))

    fun withGroupedBudget(
        enabled: Boolean,
        distributions: List<GroupedBudgetReportConfiguration.BudgetDistribution> = listOf(),
    ) = copy(groupedBudget = GroupedBudgetReportConfiguration(enabled, distributions))

    fun withPerformanceReport(enabled: Boolean) = copy(performance = PerformanceReportConfiguration(enabled))
}

@Serializable
data class GenericReportConfiguration(
    val enabled: Boolean,
)

@Serializable
data class ValueReportConfiguration(
    val enabled: Boolean,
    val filter: RecordFilter? = null,
)

@Serializable
data class NetReportConfiguration(
    val enabled: Boolean,
    val filter: RecordFilter? = null,
)

@Serializable
data class GroupedBudgetReportConfiguration(
    val enabled: Boolean,
    val distributions: List<BudgetDistribution>,
) {
    private val defaultDistribution: BudgetDistribution? = distributions.find { it.default }

    @Serializable
    data class BudgetDistribution(
        val default: Boolean,
        val from: YearMonth?,
        val groups: List<GroupBudgetPercentage>,
    )

    @Serializable
    data class GroupBudgetPercentage(
        val group: String,
        val percentage: Int,
    )

    fun getDistributionByDate(date: LocalDate): BudgetDistribution {
        return distributions
            .filter { it.from != null && LocalDate(it.from.year, it.from.month, 1) <= date }
            .maxByOrNull { it.from!! }
            ?: defaultDistribution
            ?: error("no default distribution")
    }
}

@Serializable
data class PerformanceReportConfiguration(
    val enabled: Boolean,
)

@Serializable
data class ForecastConfiguration(
    val inputBuckets: Int,
)
