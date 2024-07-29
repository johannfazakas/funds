package ro.jf.bk.fund.service.domain.command

import java.util.*

data class CreateFundCommand(
    val userId: UUID,
    val name: String
)
