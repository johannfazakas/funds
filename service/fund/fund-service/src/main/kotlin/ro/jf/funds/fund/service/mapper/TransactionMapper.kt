package ro.jf.funds.fund.service.mapper

import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.domain.TransactionRecord

fun Transaction.toTO(): TransactionTO = when (this) {
    is Transaction.SingleRecord -> TransactionTO.SingleRecord(
        id = id,
        userId = userId,
        externalId = externalId,
        dateTime = dateTime,
        record = record.toTO(),
    )
    is Transaction.Transfer -> TransactionTO.Transfer(
        id = id,
        userId = userId,
        externalId = externalId,
        dateTime = dateTime,
        sourceRecord = sourceRecord.toTO(),
        destinationRecord = destinationRecord.toTO(),
    )
    is Transaction.Exchange -> TransactionTO.Exchange(
        id = id,
        userId = userId,
        externalId = externalId,
        dateTime = dateTime,
        sourceRecord = sourceRecord.toTO(),
        destinationRecord = destinationRecord.toTO(),
        feeRecord = feeRecord?.toTO(),
    )
    is Transaction.OpenPosition -> TransactionTO.OpenPosition(
        id = id,
        userId = userId,
        externalId = externalId,
        dateTime = dateTime,
        currencyRecord = currencyRecord.toTO(),
        instrumentRecord = instrumentRecord.toTO(),
    )
    is Transaction.ClosePosition -> TransactionTO.ClosePosition(
        id = id,
        userId = userId,
        externalId = externalId,
        dateTime = dateTime,
        currencyRecord = currencyRecord.toTO(),
        instrumentRecord = instrumentRecord.toTO(),
    )
}

fun TransactionRecord.toTO() = TransactionRecordTO(
    id = id,
    accountId = accountId,
    fundId = fundId,
    amount = amount,
    unit = unit,
    labels = labels,
)
