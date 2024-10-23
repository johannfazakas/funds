package ro.jf.funds.account.service.domain

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.model.FinancialUnit
import java.util.*


data class Account(
    val id: UUID,
    val userId: UUID,
    val name: AccountName,
    val unit: FinancialUnit,
)
