package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDateTime
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
sealed class ReportViewDataTO(
    val reportViewType: ReportViewType,
) {
    abstract val viewId: UUID
    abstract val viewName: String

    @Serializable(with = UUIDSerializer::class)
    abstract val fundId: UUID
    abstract val granularity: DataGranularity
}

@Serializable
@SerialName("expense")
data class ExpenseReportViewDataTO(
    @Serializable(with = UUIDSerializer::class)
    override val viewId: UUID,
    override val viewName: String,
    @Serializable(with = UUIDSerializer::class)
    override val fundId: UUID,
    override val granularity: DataGranularity,
    val data: List<DataItem>,
) : ReportViewDataTO(ReportViewType.EXPENSE) {
    @Serializable
    data class DataItem(
        val timeBucket: LocalDateTime,
        @Serializable(with = BigDecimalSerializer::class)
        val amount: BigDecimal,
    )
}
