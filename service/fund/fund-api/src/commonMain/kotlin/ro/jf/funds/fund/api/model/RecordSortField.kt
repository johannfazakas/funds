package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.SortField

@Serializable
enum class RecordSortField(override val value: String) : SortField {
    DATE("date"),
    AMOUNT("amount")
}
