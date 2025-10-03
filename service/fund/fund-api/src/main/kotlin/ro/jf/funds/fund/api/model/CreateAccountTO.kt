package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit

@Serializable
data class CreateAccountTO(
    val name: AccountName,
    val unit: FinancialUnit,
)
