package ro.jf.bk.fund.service.mapper

import ro.jf.bk.fund.service.domain.FundRecord
import ro.jf.bk.fund.service.domain.FundTransaction
import ro.jf.bk.fund.service.service.METADATA_FUND_ID
import java.util.*
import ro.jf.bk.account.api.model.AccountRecordTO as AccountRecordTO
import ro.jf.bk.account.api.model.AccountTransactionTO as AccountTransactionTO

fun AccountTransactionTO.toTransaction(userId: UUID) = FundTransaction(
    id = id,
    userId = userId,
    dateTime = dateTime,
    records = records.map { it.toRecord() }
)

fun AccountRecordTO.toRecord() = FundRecord(
    id = id,
    accountId = accountId,
    amount = amount,
    fundId = metadata[METADATA_FUND_ID]
        ?.let(UUID::fromString)
        ?: error("Fund id not found in metadata")
)

