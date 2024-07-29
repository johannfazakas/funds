package ro.jf.bk.fund.service.adapter.mapper

import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.fund.Fund
import java.util.*

fun Fund.toTO() = FundTO(
    id = id,
    name = name,
)

fun CreateFundTO.toCommand(userId: UUID) = CreateFundCommand(
    userId = userId,
    name = name,
)
