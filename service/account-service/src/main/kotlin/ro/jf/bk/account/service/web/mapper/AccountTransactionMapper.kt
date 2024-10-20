package ro.jf.bk.account.service.web.mapper

import ro.jf.bk.account.api.model.AccountRecordTO
import ro.jf.bk.account.api.model.AccountTransactionTO
import ro.jf.bk.account.service.domain.AccountRecord
import ro.jf.bk.account.service.domain.AccountTransaction

fun AccountTransaction.toTO(): AccountTransactionTO {
    return AccountTransactionTO(
        id = id,
        dateTime = dateTime,
        records = records.map(AccountRecord::toTO),
        metadata = metadata,
    )
}

fun AccountRecord.toTO() = AccountRecordTO(
    id = id,
    accountId = accountId,
    amount = amount,
    metadata = metadata,
)
