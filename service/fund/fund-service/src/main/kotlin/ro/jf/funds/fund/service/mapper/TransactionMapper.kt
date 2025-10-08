package ro.jf.funds.fund.service.mapper

import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.domain.TransactionRecord

fun Transaction.toTO() = TransactionTO(
    id = id,
    userId = userId,
    externalId = externalId,
    type = type,
    dateTime = dateTime,
    records = records.map { it.toTO() },
)

fun TransactionRecord.toTO() = TransactionRecordTO(
    id = id,
    accountId = accountId,
    fundId = fundId,
    amount = amount,
    unit = unit,
    labels = labels,
)
