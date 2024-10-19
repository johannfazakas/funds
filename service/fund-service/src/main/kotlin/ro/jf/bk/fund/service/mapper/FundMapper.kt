package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.api.model.FundAccountTO
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.domain.Fund
import ro.jf.bk.fund.service.domain.FundAccount

fun Fund.toTO() = FundTO(
    id = id,
    name = name,
    accounts = accounts.map { it.toTO() }
)

fun FundAccount.toTO() = FundAccountTO(
    id = id
)
