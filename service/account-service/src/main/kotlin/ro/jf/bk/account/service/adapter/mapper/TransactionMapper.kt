package ro.jf.bk.account.service.adapter.mapper

import ro.jf.bk.account.api.model.RecordTO
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.account.service.domain.model.Record
import ro.jf.bk.account.service.domain.model.Transaction

fun Transaction.toTO(): TransactionTO {
    return TransactionTO(
        id = id,
        dateTime = dateTime,
        records = records.map(Record::toTO),
        metadata = metadata,
    )
}

fun Record.toTO() = RecordTO(
    id = id,
    accountId = accountId,
    amount = amount,
    metadata = metadata,
)
