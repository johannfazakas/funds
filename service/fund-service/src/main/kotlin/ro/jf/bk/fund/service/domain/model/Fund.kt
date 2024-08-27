package ro.jf.bk.fund.service.domain.model

import java.util.*

data class Fund(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val accounts: List<FundAccount>
)

data class FundAccount(
    val id: UUID
)
