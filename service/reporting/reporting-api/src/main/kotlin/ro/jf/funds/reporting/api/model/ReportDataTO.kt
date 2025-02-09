package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class ReportDataTO {
    abstract val viewId: UUID
    abstract val granularInterval: GranularDateInterval
}

@Serializable
@SerialName("EXPENSE")
data class ExpenseReportDataTO(
    @Serializable(with = UUIDSerializer::class)
    override val viewId: UUID,
    override val granularInterval: GranularDateInterval,
    val data: List<DataItem>,
) : ReportDataTO() {
    @Serializable
    data class DataItem(
        val timeBucket: LocalDate,
        // TODO(Johann) should also have a `net` and a `groupedDataNet`
        @Serializable(with = BigDecimalSerializer::class)
        val amount: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val minValue: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val maxValue: BigDecimal,
    )
}

// TODO(Johann) will features still be used?
// TODO(Johann) will probably not need sealed derivates, this could be inlined
@Serializable
@SerialName("FEATURES")
data class FeaturesReportDataTO(
    @Serializable(with = UUIDSerializer::class)
    override val viewId: UUID,
    override val granularInterval: GranularDateInterval,
    val features: List<FeatureReportDataTO>,
) : ReportDataTO()

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class FeatureReportDataTO {
    @Serializable
    @SerialName("min_max_total_value")
    data class MinMaxTotalValue(
        val data: List<DataItem>,
    ) : FeatureReportDataTO() {
        @Serializable
        data class DataItem(
            val timeBucket: LocalDate,
            @Serializable(with = BigDecimalSerializer::class)
            val minValue: BigDecimal,
            @Serializable(with = BigDecimalSerializer::class)
            val maxValue: BigDecimal,
        )
    }
}
