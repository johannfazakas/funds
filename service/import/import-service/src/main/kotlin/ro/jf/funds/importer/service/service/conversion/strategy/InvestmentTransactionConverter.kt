package ro.jf.funds.importer.service.service.conversion.strategy

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import ro.jf.funds.platform.api.model.Category
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord

class InvestmentTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): Boolean {
        if (transaction.records.size != 2) return false
        val currencyRecords = transaction.records.filter { it.unit is Currency }
        if (currencyRecords.size != 1) return false
        val instrumentRecords = transaction.records.filter { it.unit is Instrument }
        if (instrumentRecords.size != 1) return false

        val currencyRecord = currencyRecords.first()
        val instrumentRecord = instrumentRecords.first()

        if (currencyRecord.amount > BigDecimal.ZERO && instrumentRecord.amount > BigDecimal.ZERO ||
            currencyRecord.amount < BigDecimal.ZERO && instrumentRecord.amount < BigDecimal.ZERO
        )
            return false

        if (instrumentRecord.unit != accountStore[instrumentRecord.accountId].unit) return false
        return true
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): List<Conversion> {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val currencyAccount = accountStore[currencyRecord.accountId]

        return if (currencyRecord.unit != currencyAccount.unit) {
            val targetCurrency = currencyAccount.unit as? Currency
                ?: throw ImportDataException("Unit ${currencyAccount.unit} is not a currency, conversion would not be supported: $transaction")
            listOf(
                Conversion(
                    transaction.dateTime.date,
                    currencyRecord.unit,
                    targetCurrency
                )
            )
        } else {
            listOf()
        }
    }

    override fun mapToTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        accountStore: Store<AccountTO>,
        categoryStore: Store<CategoryTO>,
    ): CreateTransactionTO {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val instrumentRecord = transaction.records.first { it.unit is Instrument }

        val currencyRecordTO = currencyRecord.toImportCurrencyFundRecord(
            date = transaction.dateTime.date,
            account = accountStore[currencyRecord.accountId],
            conversions = conversions,
            categoryStore = categoryStore,
        )
        val instrumentRecordTO = CreateTransactionRecordTO.InstrumentRecord(
            fundId = instrumentRecord.fundId,
            accountId = instrumentRecord.accountId,
            amount = instrumentRecord.amount,
            unit = instrumentRecord.unit as Instrument,
            category = instrumentRecord.categoryId?.let { Category(categoryStore[it].name) },
            note = instrumentRecord.note,
        )
        return when {
            currencyRecord.amount < BigDecimal.ZERO && instrumentRecord.amount > BigDecimal.ZERO ->
                CreateTransactionTO.OpenPosition(
                    dateTime = transaction.dateTime,
                    externalId = transaction.transactionExternalId,
                    currencyRecord = currencyRecordTO,
                    instrumentRecord = instrumentRecordTO
                )
            currencyRecord.amount > BigDecimal.ZERO && instrumentRecord.amount < BigDecimal.ZERO ->
                CreateTransactionTO.ClosePosition(
                    dateTime = transaction.dateTime,
                    externalId = transaction.transactionExternalId,
                    currencyRecord = currencyRecordTO,
                    instrumentRecord = instrumentRecordTO
                )
            else -> throw ImportDataException("Invalid investment transaction, currency and instrument amounts must have opposite signs: $transaction")
        }
    }
}
