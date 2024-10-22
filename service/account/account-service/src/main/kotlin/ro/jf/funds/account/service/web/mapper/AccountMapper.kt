package ro.jf.funds.account.service.web.mapper

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.service.domain.Account

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
