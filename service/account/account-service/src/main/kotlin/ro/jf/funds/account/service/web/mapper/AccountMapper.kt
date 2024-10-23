package ro.jf.funds.account.service.web.mapper

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.service.domain.Account

fun Account.toTO() = AccountTO(
    id = id,
    name = name,
    unit = unit
)
