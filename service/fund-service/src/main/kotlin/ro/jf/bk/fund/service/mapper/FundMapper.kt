package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.domain.Fund

fun Fund.toTO() = FundTO(
    id = id,
    name = name
)
