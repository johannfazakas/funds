package ro.jf.funds.fund.service.domain

import ro.jf.funds.fund.api.model.FundName
import java.util.*

data class Fund(
    val id: UUID,
    val userId: UUID,
    val name: FundName,
)

data class FundAccount(
    val id: UUID
)
