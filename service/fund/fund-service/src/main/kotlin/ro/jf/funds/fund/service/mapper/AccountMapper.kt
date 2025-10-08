package ro.jf.funds.fund.service.mapper

import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.service.domain.Account

fun Account.toTO() = AccountTO(
    id = id,
    name = name,
    unit = unit
)