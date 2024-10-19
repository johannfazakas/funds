package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.service.domain.Record
import ro.jf.bk.fund.service.domain.Transaction
import ro.jf.bk.fund.service.service.METADATA_FUND_ID
import java.util.*
import ro.jf.bk.account.api.model.RecordTO as AccountRecordTO
import ro.jf.bk.account.api.model.TransactionTO as AccountTransactionTO

fun AccountTransactionTO.toTransaction(userId: UUID) = Transaction(
    id = id,
    userId = userId,
    dateTime = dateTime,
    records = records.map { it.toRecord() }
)

fun AccountRecordTO.toRecord() = Record(
    id = id,
    accountId = accountId,
    amount = amount,
    fundId = metadata[METADATA_FUND_ID]
        ?.let(UUID::fromString)
        ?: error("Fund id not found in metadata")
)

