package ro.jf.bk.account.service.domain.command

import ro.jf.bk.account.api.model.AccountName
import java.util.*

data class CreateCurrencyAccountCommand(
    val userId: UUID,
    val name: AccountName,
    val currency: String,
)

data class CreateInstrumentAccountCommand(
    val userId: UUID,
    val name: AccountName,
    val currency: String,
    val symbol: String,
)


