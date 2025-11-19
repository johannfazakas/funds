package ro.jf.funds.fund.service.domain

import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.fund.api.model.AccountName
import java.util.*

data class Account(
    // TODO(Johann) UUID? or Kmp Uuid
    val id: UUID,
    val userId: UUID,
    val name: AccountName,
    val unit: FinancialUnit,
)