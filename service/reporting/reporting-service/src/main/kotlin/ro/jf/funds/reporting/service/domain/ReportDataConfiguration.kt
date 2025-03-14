package ro.jf.funds.reporting.service.domain

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.reporting.api.model.YearMonth

@Serializable
data class ReportDataConfiguration(
    val currency: Currency,
    val filter: RecordFilter = RecordFilter(),
    val groups: List<ReportGroup>? = null,
    val features: ReportDataFeaturesConfiguration,
) {
    fun withFilter(labels: List<Label>) = copy(filter = RecordFilter(labels))
    fun withGroups(vararg groups: ReportGroup) = copy(groups = groups.toList())
    fun withNet(enabled: Boolean, applyFilter: Boolean) = copy(features = features.withNet(enabled, applyFilter))
    fun withValueReport(enabled: Boolean) = copy(features = features.withValueReport(enabled))
}

@Serializable
data class ReportGroup(
    val name: String,
    val filter: RecordFilter,
)

@Serializable
data class RecordFilter(
    val labels: List<Label>? = null,
) {
    companion object {
        fun byLabels(vararg labels: String): RecordFilter = RecordFilter(labels.map(::Label))
    }

    fun test(reportRecord: ReportRecord): Boolean =
        labels?.any { reportRecord.labels.contains(it) } ?: true
}

@Serializable
data class ReportDataFeaturesConfiguration(
    val net: NetReportFeature = NetReportFeature(enabled = false, applyFilter = false),
    val valueReport: GenericReportFeature = GenericReportFeature(enabled = false),
    val groupedNet: GenericReportFeature = GenericReportFeature(enabled = false),
    val groupedBudget: GroupedBudgetReportFeature = GroupedBudgetReportFeature(enabled = false, listOf()),
) {
    fun withNet(enabled: Boolean, applyFilter: Boolean) = copy(net = NetReportFeature(enabled, applyFilter))
    fun withGroupedNet(enabled: Boolean) = copy(groupedNet = GenericReportFeature(enabled))
    fun withValueReport(enabled: Boolean) = copy(valueReport = GenericReportFeature(enabled))
    fun withGroupedBudget(
        enabled: Boolean,
        distributions: List<GroupedBudgetReportFeature.BudgetDistribution> = listOf(),
    ) = copy(groupedBudget = GroupedBudgetReportFeature(enabled, distributions))
}

@Serializable
data class GenericReportFeature(
    val enabled: Boolean,
)

@Serializable
data class NetReportFeature(
    val enabled: Boolean,
    val applyFilter: Boolean,
)

@Serializable
data class GroupedBudgetReportFeature(
    val enabled: Boolean,
    val distributions: List<BudgetDistribution>,
) {
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
}
