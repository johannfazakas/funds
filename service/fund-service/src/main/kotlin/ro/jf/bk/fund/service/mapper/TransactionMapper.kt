package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.api.model.RecordTO
import ro.jf.bk.fund.api.model.TransactionTO
import ro.jf.bk.fund.service.domain.Record
import ro.jf.bk.fund.service.domain.Transaction

fun Transaction.toTO() = TransactionTO(
    id = id,
    userId = userId,
    dateTime = dateTime,
    records = records.map { it.toTO() }
)

fun Record.toTO() = RecordTO(
    id = id,
    fundId = fundId,
    accountId = accountId,
    amount = amount,
)
