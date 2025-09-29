package ro.jf.funds.fund.service.mapper

import ro.jf.funds.fund.api.model.FundRecordTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.service.domain.FundRecord
import ro.jf.funds.fund.service.domain.FundTransaction

fun FundTransaction.toTO() = FundTransactionTO(
    id = id,
    userId = userId,
    type = type,
    dateTime = dateTime,
    records = records.map { it.toTO() }
)

fun FundRecord.toTO() = FundRecordTO(
    id = id,
    fundId = fundId,
    accountId = accountId,
    amount = amount,
    unit = unit,
    labels = labels
)
