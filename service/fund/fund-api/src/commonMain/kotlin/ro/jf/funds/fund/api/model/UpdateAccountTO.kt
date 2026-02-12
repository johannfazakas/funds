package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.FinancialUnit

@Serializable
data class UpdateAccountTO(
    val name: AccountName? = null,
    val unit: FinancialUnit? = null,
)
