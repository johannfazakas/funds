package ro.jf.funds.client.web.api

import com.benasher44.uuid.uuidFrom
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.client.sdk.TransactionClient
import ro.jf.funds.client.web.model.JsCreateRecord
import ro.jf.funds.client.web.model.JsTransaction
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import kotlin.js.JsExport
import kotlin.js.Promise

@JsExport
object TransactionApi {
    private val config = js("window.FUNDS_CONFIG")
    private val fundServiceUrl: String = config?.fundServiceUrl as? String ?: "http://localhost:5253"
    private val transactionClient = TransactionClient(baseUrl = fundServiceUrl)

    fun getTransaction(
        userId: String,
        transactionId: String,
    ): Promise<JsTransaction> = GlobalScope.promise {
        val transaction = transactionClient.getTransaction(uuidFrom(userId), uuidFrom(transactionId))
        JsTransaction(transaction)
    }

    fun listTransactions(
        userId: String,
        fromDate: String?,
        toDate: String?,
        fundId: String?,
        accountId: String?,
    ): Promise<Array<JsTransaction>> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val filter = TransactionFilterTO(
            fromDate = fromDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            toDate = toDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            fundId = fundId?.takeIf { it.isNotBlank() }?.let { uuidFrom(it) },
            accountId = accountId?.takeIf { it.isNotBlank() }?.let { uuidFrom(it) },
        )
        val transactions = transactionClient.listTransactions(uuid, filter)
        transactions.map { JsTransaction(it) }.toTypedArray()
    }

    fun createTransaction(
        userId: String,
        type: String,
        dateTime: String,
        externalId: String,
        records: Array<JsCreateRecord>,
    ): Promise<JsTransaction> = GlobalScope.promise {
        val request = buildCreateTransactionTO(type, dateTime, externalId, records.toList())
        val transaction = transactionClient.createTransaction(uuidFrom(userId), request)
        JsTransaction(transaction)
    }

    fun deleteTransaction(userId: String, transactionId: String): Promise<Unit> = GlobalScope.promise {
        transactionClient.deleteTransaction(uuidFrom(userId), uuidFrom(transactionId))
    }

    private fun buildCreateTransactionTO(
        type: String,
        dateTime: String,
        externalId: String,
        records: List<JsCreateRecord>,
    ): CreateTransactionTO {
        val byRole = records.associateBy { it.role }
        val parsedDateTime = LocalDateTime.parse(dateTime)
        return when (TransactionType.valueOf(type)) {
            TransactionType.SINGLE_RECORD -> CreateTransactionTO.SingleRecord(
                dateTime = parsedDateTime,
                externalId = externalId,
                record = byRole.getValue("record").toCurrencyRecord(),
            )

            TransactionType.TRANSFER -> CreateTransactionTO.Transfer(
                dateTime = parsedDateTime,
                externalId = externalId,
                sourceRecord = byRole.getValue("sourceRecord").toCurrencyRecord(),
                destinationRecord = byRole.getValue("destinationRecord").toCurrencyRecord(),
            )

            TransactionType.EXCHANGE -> CreateTransactionTO.Exchange(
                dateTime = parsedDateTime,
                externalId = externalId,
                sourceRecord = byRole.getValue("sourceRecord").toCurrencyRecord(),
                destinationRecord = byRole.getValue("destinationRecord").toCurrencyRecord(),
                feeRecord = byRole["feeRecord"]?.toCurrencyRecord(),
            )

            TransactionType.OPEN_POSITION -> CreateTransactionTO.OpenPosition(
                dateTime = parsedDateTime,
                externalId = externalId,
                currencyRecord = byRole.getValue("currencyRecord").toCurrencyRecord(),
                instrumentRecord = byRole.getValue("instrumentRecord").toInstrumentRecord(),
            )

            TransactionType.CLOSE_POSITION -> CreateTransactionTO.ClosePosition(
                dateTime = parsedDateTime,
                externalId = externalId,
                currencyRecord = byRole.getValue("currencyRecord").toCurrencyRecord(),
                instrumentRecord = byRole.getValue("instrumentRecord").toInstrumentRecord(),
            )
        }
    }


    private fun JsCreateRecord.toCurrencyRecord(): CreateTransactionRecordTO.CurrencyRecord =
        CreateTransactionRecordTO.CurrencyRecord(
            accountId = uuidFrom(accountId),
            fundId = uuidFrom(fundId),
            amount = BigDecimal.parseString(amount),
            unit = Currency(unitValue),
            labels = labels.map(::Label),
        )

    private fun JsCreateRecord.toInstrumentRecord(): CreateTransactionRecordTO.InstrumentRecord =
        CreateTransactionRecordTO.InstrumentRecord(
            accountId = uuidFrom(accountId),
            fundId = uuidFrom(fundId),
            amount = BigDecimal.parseString(amount),
            unit = Instrument(unitValue),
            labels = labels.map(::Label),
        )
}
