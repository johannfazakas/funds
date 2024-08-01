package ro.jf.bk.fund.service.adapter.mapper

import ro.jf.bk.fund.api.model.CreateFundAccountTO
import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundAccountTO
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.domain.command.CreateFundAccountCommand
import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import ro.jf.bk.fund.service.domain.model.FundAccount
import java.util.*

fun Fund.toTO() = FundTO(
    id = id,
    name = name,
    accounts = accounts.map { it.toTO() }
)

fun FundAccount.toTO() = FundAccountTO(
    accountId = accountId
)

fun CreateFundTO.toCommand(userId: UUID) = CreateFundCommand(
    userId = userId,
    name = name,
    accounts = accounts.map { it.toCommand() }
)

fun CreateFundAccountTO.toCommand() = CreateFundAccountCommand(
    accountId = accountId
)
