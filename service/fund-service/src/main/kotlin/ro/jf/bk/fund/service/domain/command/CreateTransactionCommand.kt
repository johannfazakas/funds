package ro.jf.bk.fund.service.domain.command

import java.util.*

data class CreateTransactionCommand(
    val userId: UUID
    // TODO(Johann) other fields currently missing
)
