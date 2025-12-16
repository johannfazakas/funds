package ro.jf.funds.fund.service.domain

import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.fund.api.model.AccountName
import java.util.*

data class Account(
    val id: UUID,
    val userId: UUID,
    val name: AccountName,
    val unit: FinancialUnit,
)