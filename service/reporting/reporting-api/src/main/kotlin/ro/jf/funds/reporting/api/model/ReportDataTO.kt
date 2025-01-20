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
sealed class ReportDataTO(
    val reportViewType: ReportViewType,
) {
    abstract val viewId: UUID
    abstract val granularInterval: GranularDateInterval
}

@Serializable
@SerialName("expense")
data class ExpenseReportDataTO(
    @Serializable(with = UUIDSerializer::class)
    override val viewId: UUID,
    override val granularInterval: GranularDateInterval,
    val data: List<DataItem>,
) : ReportDataTO(ReportViewType.EXPENSE) {
    @Serializable
    data class DataItem(
        val timeBucket: LocalDate,
        @Serializable(with = BigDecimalSerializer::class)
        val amount: BigDecimal,
    )
}
