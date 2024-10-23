package ro.jf.funds.account.service.web.mapper

import ro.jf.funds.account.api.model.AccountRecordTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.account.service.domain.AccountRecord
import ro.jf.funds.account.service.domain.AccountTransaction

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
    unit = unit,
    metadata = metadata,
)
