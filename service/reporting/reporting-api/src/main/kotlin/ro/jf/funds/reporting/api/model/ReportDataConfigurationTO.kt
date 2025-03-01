package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label

@Serializable
data class ReportDataConfigurationTO(
    val currency: Currency,
    val filter: RecordFilterTO,
    val groups: List<ReportGroupTO>? = null,
    val features: ReportDataFeaturesConfigurationTO,
)

@Serializable
data class ReportGroupTO(
    val name: String,
    val filter: RecordFilterTO,
)

@Serializable
data class RecordFilterTO(
    val labels: List<Label>?,
) {
    companion object {
        fun byLabels(vararg labels: String): RecordFilterTO = RecordFilterTO(labels.map(::Label))
    }
}

@Serializable
data class ReportDataFeaturesConfigurationTO(
    val net: NetReportFeatureTO = NetReportFeatureTO(enabled = false, applyFilter = false),
    val valueReport: GenericReportFeatureTO = GenericReportFeatureTO(false),
)

@Serializable
data class GenericReportFeatureTO(
    val enabled: Boolean,
)

@Serializable
data class NetReportFeatureTO(
    val enabled: Boolean,
    val applyFilter: Boolean,
)
