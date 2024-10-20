package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.api.model.FundRecordTO
import ro.jf.bk.fund.api.model.FundTransactionTO
import ro.jf.bk.fund.service.domain.FundRecord
import ro.jf.bk.fund.service.domain.FundTransaction

fun FundTransaction.toTO() = FundTransactionTO(
    id = id,
    userId = userId,
    dateTime = dateTime,
    records = records.map { it.toTO() }
)

fun FundRecord.toTO() = FundRecordTO(
    id = id,
    fundId = fundId,
    accountId = accountId,
    amount = amount,
)
