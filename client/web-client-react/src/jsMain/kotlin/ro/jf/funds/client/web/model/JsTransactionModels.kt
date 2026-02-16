package ro.jf.funds.client.web.model

import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
data class JsCreateRecord(
    val role: String,
    val accountId: String,
    val fundId: String,
    val amount: String,
    val unitType: String,
    val unitValue: String,
    val labels: Array<String> = emptyArray(),
)

@JsExport
data class JsTransaction(
    val id: String,
    val userId: String,
    val externalId: String,
    val dateTime: String,
    val type: String,
    val records: Array<JsTransactionRecord>,
) {
    @JsName("fromTransactionTO")
    constructor(to: TransactionTO) : this(
        id = to.id.toString(),
        userId = to.userId.toString(),
        externalId = to.externalId,
        dateTime = to.dateTime.toString(),
        type = to.type.name,
        records = to.records.map { JsTransactionRecord(it) }.toTypedArray(),
    )
}

@JsExport
data class JsTransactionRecord(
    val id: String,
    val accountId: String,
    val fundId: String,
    val amount: String,
    val unitType: String,
    val unitValue: String,
    val recordType: String,
    val labels: Array<String>,
    val note: String?,
) {
    @JsName("fromCurrencyRecord")
    constructor(to: TransactionRecordTO.CurrencyRecord) : this(
        id = to.id.toString(),
        accountId = to.accountId.toString(),
        fundId = to.fundId.toString(),
        amount = to.amount.toPlainString(),
        unitType = "currency",
        unitValue = to.unit.value,
        recordType = "CURRENCY",
        labels = to.labels.map { it.value }.toTypedArray(),
        note = to.note,
    )

    @JsName("fromInstrumentRecord")
    constructor(to: TransactionRecordTO.InstrumentRecord) : this(
        id = to.id.toString(),
        accountId = to.accountId.toString(),
        fundId = to.fundId.toString(),
        amount = to.amount.toPlainString(),
        unitType = "instrument",
        unitValue = to.unit.value,
        recordType = "INSTRUMENT",
        labels = to.labels.map { it.value }.toTypedArray(),
        note = to.note,
    )

    @JsName("fromTransactionRecordTO")
    constructor(to: TransactionRecordTO) : this(
        id = to.id.toString(),
        accountId = to.accountId.toString(),
        fundId = to.fundId.toString(),
        amount = to.amount.toPlainString(),
        unitType = when (to) { is TransactionRecordTO.CurrencyRecord -> "currency"; is TransactionRecordTO.InstrumentRecord -> "instrument" },
        unitValue = to.unit.value,
        recordType = when (to) { is TransactionRecordTO.CurrencyRecord -> "CURRENCY"; is TransactionRecordTO.InstrumentRecord -> "INSTRUMENT" },
        labels = to.labels.map { it.value }.toTypedArray(),
        note = to.note,
    )
}
