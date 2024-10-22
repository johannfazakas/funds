package ro.jf.funds.fund.service.mapper

import ro.jf.funds.account.api.model.AccountRecordTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.fund.service.domain.FundRecord
import ro.jf.funds.fund.service.domain.FundTransaction
import ro.jf.funds.fund.service.service.METADATA_FUND_ID
import java.util.*

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

