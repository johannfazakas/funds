package ro.jf.bk.fund.service.domain.model

import ro.jf.bk.fund.api.model.FundName
import java.util.*

data class Fund(
    val id: UUID,
    val userId: UUID,
    val name: FundName,
    val accounts: List<FundAccount>
)

data class FundAccount(
    val id: UUID
)
