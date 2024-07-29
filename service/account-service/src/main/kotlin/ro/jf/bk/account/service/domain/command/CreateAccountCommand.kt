package ro.jf.bk.account.service.domain.command

import java.util.*

data class CreateCurrencyAccountCommand(
    val userId: UUID,
    val name: String,
    val currency: String,
)

data class CreateInstrumentAccountCommand(
    val userId: UUID,
    val name: String,
    val currency: String,
    val symbol: String,
)


