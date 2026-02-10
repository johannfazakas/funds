package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.SortField

@Serializable
enum class AccountSortField(override val value: String) : SortField {
    NAME("name"),
    UNIT("unit")
}
