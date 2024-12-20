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
        properties = properties.groupBy { it.key }.mapValues { (_, props) -> props.map { it.value } },
    )
}

fun AccountRecord.toTO() = AccountRecordTO(
    id = id,
    accountId = accountId,
    amount = amount,
    unit = unit,
    properties = properties.groupBy { it.key }.mapValues { (_, props) -> props.map { it.value } },
)
