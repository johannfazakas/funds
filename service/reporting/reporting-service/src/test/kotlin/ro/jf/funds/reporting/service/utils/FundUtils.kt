package ro.jf.funds.reporting.service.utils

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.api.model.TransactionType
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

fun transaction(
    userId: UUID,
    dateTime: LocalDateTime,
    type: TransactionType,
    records: List<TransactionRecordTO>,
): TransactionTO =
    TransactionTO(randomUUID(), userId, randomUUID().toString(), type, dateTime, records)

fun record(
    fundId: UUID,
    accountId: UUID,
    amount: BigDecimal,
    currency: Currency,
    labels: List<Label>,
): TransactionRecordTO =
    TransactionRecordTO(randomUUID(), accountId, fundId, amount, currency, labels)
