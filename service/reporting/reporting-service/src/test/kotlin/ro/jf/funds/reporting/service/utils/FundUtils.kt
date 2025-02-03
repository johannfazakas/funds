package ro.jf.funds.reporting.service.utils

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.fund.api.model.FundRecordTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

fun transaction(
    userId: UUID,
    dateTime: LocalDateTime,
    records: List<FundRecordTO>,
): FundTransactionTO =
    FundTransactionTO(randomUUID(), userId, dateTime, records)

fun record(
    fundId: UUID,
    accountId: UUID,
    amount: BigDecimal,
    currency: Currency,
    labels: List<Label>
): FundRecordTO =
    FundRecordTO(randomUUID(), fundId, accountId, amount, currency, labels)