package ro.jf.bk.account.service.adapter.mapper

import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import java.util.*

fun Account.toTO() = when (this) {
    is Account.Currency -> AccountTO.Currency(
        id = id,
        name = name,
        currency = currency
    )

    is Account.Instrument -> AccountTO.Instrument(
        id = id,
        name = name,
        currency = currency,
        symbol = symbol
    )
}

fun CreateCurrencyAccountTO.toCommand(userId: UUID) = CreateCurrencyAccountCommand(
    userId = userId,
    name = name,
    currency = currency
)

fun CreateInstrumentAccountTO.toCommand(userId: UUID) = CreateInstrumentAccountCommand(
    userId = userId,
    name = name,
    currency = currency,
    symbol = symbol
)
