package ro.jf.funds.account.service.web.mapper

import ro.jf.funds.account.api.model.AccountRecordTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.account.api.model.PropertyTO
import ro.jf.funds.account.service.domain.AccountRecord
import ro.jf.funds.account.service.domain.AccountTransaction

fun AccountTransaction.toTO(): AccountTransactionTO = AccountTransactionTO(
    id = id,
    dateTime = dateTime,
    records = records.map(AccountRecord::toTO),
    properties = properties.map { PropertyTO(it.key to it.value) },
)

fun AccountRecord.toTO() = AccountRecordTO(
    id = id,
    accountId = accountId,
    amount = amount,
    unit = unit,
    properties = properties.map { PropertyTO(it.key to it.value) },
    labels = labels,
)
