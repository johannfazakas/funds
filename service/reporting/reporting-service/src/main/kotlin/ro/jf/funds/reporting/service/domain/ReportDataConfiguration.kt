package ro.jf.funds.reporting.service.domain

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label

@Serializable
data class ReportDataConfiguration(
    val currency: Currency,
    val filter: RecordFilter,
    val groups: List<ReportGroup>? = null,
    val features: ReportDataFeaturesConfiguration,
) {

}

@Serializable
data class ReportGroup(
    val name: String,
    val filter: RecordFilter,
)

@Serializable
data class RecordFilter(
    val labels: List<Label>?,
) {
    companion object {
        fun byLabels(vararg labels: String): RecordFilter = RecordFilter(labels.map(::Label))
    }
}

@Serializable
data class ReportDataFeaturesConfiguration(
    val net: NetReportFeature = NetReportFeature(enabled = false, applyFilter = false),
    val valueReport: GenericReportFeature = GenericReportFeature(enabled = false),
) {
    fun withNet(enabled: Boolean, applyFilter: Boolean) = copy(net = NetReportFeature(enabled, applyFilter))
    fun withValueReport(enabled: Boolean) = copy(valueReport = GenericReportFeature(enabled))
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
