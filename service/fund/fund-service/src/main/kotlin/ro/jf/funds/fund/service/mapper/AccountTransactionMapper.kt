package ro.jf.funds.fund.service.mapper

import ro.jf.funds.account.api.model.AccountRecordTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.fund.service.domain.FundRecord
import ro.jf.funds.fund.service.domain.FundTransaction
import ro.jf.funds.fund.service.service.FUND_ID_PROPERTY
import java.util.*

fun AccountTransactionTO.toTransaction(userId: UUID) = FundTransaction(
    id = id,
    userId = userId,
    dateTime = dateTime,
    records = records.map { it.toRecord() }
)

fun AccountRecordTO.toRecord() = FundRecord(
    id = id,
    accountId = accountId,
    amount = amount,
    fundId = properties
        .single { it.key == FUND_ID_PROPERTY }.value
        .let(UUID::fromString)
)
