package ro.jf.bk.fund.service.domain.command

import ro.jf.bk.fund.api.model.FundName
import java.util.*

data class CreateFundCommand(
    val userId: UUID,
    val name: FundName,
    val accounts: List<CreateFundAccountCommand>
)

data class CreateFundAccountCommand(
    val accountId: UUID
)
