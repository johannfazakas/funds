package ro.jf.funds.fund.service.mapper

import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.service.domain.Fund

fun Fund.toTO() = FundTO(
    id = id,
    name = name
)
