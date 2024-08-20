package ro.jf.bk.fund.service.adapter.mapper

import ro.jf.bk.fund.api.model.RecordTO
import ro.jf.bk.fund.api.model.TransactionTO
import ro.jf.bk.fund.service.domain.model.Transaction
import ro.jf.bk.fund.service.domain.model.Record

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
