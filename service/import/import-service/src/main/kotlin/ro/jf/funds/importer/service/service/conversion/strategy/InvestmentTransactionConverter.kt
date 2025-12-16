package ro.jf.funds.importer.service.service.conversion.strategy

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.fund.api.model.*
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
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size != 2) return false
        val currencyRecords = transaction.records.filter { it.unit is Currency }
        if (currencyRecords.size != 1) return false
        val instrumentRecords = transaction.records.filter { it.unit is Instrument }
        if (instrumentRecords.size != 1) return false

        val currencyRecord = currencyRecords.first()
        val instrumentRecord = instrumentRecords.first()

        // the currency and instrument amounts should have opposite sign
        if (currencyRecord.amount > BigDecimal.ZERO && instrumentRecord.amount > BigDecimal.ZERO ||
            currencyRecord.amount < BigDecimal.ZERO && instrumentRecord.amount < BigDecimal.ZERO
        )
            return false

        if (instrumentRecord.unit != accountStore[instrumentRecord.accountName].unit) return false
        return true
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion> {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val currencyAccount = accountStore[currencyRecord.accountName]

        return if (currencyRecord.unit != currencyAccount.unit) {
            val targetCurrency = currencyAccount.unit as? Currency
                ?: throw ImportDataException("Unit ${currencyAccount.unit} is not a currency, conversion would not be supported.")
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

    override fun mapToTransactions(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): List<CreateTransactionTO> {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val instrumentRecord = transaction.records.first { it.unit is Instrument }

        val transactionType = when {
            currencyRecord.amount < BigDecimal.ZERO && instrumentRecord.amount > BigDecimal.ZERO -> TransactionType.OPEN_POSITION
            currencyRecord.amount > BigDecimal.ZERO && instrumentRecord.amount < BigDecimal.ZERO -> TransactionType.CLOSE_POSITION
            else -> throw ImportDataException("Invalid investment transaction: currency and instrument amounts must have opposite signs")
        }

        val currencyRecordTO = currencyRecord.toImportCurrencyFundRecord(
            date = transaction.dateTime.date,
            fundId = fundStore[currencyRecord.fundName].id,
            account = accountStore[currencyRecord.accountName],
            conversions = conversions,
        )
        val instrumentRecordTO = CreateTransactionRecordTO(
            fundId = fundStore[instrumentRecord.fundName].id,
            accountId = accountStore[instrumentRecord.accountName].id,
            amount = instrumentRecord.amount,
            unit = instrumentRecord.unit,
            labels = instrumentRecord.labels,
        )
        return when (transactionType) {
            TransactionType.OPEN_POSITION -> listOf(
                CreateTransactionTO.OpenPosition(
                    dateTime = transaction.dateTime,
                    externalId = transaction.transactionExternalId,
                    currencyRecord = currencyRecordTO,
                    instrumentRecord = instrumentRecordTO
                )
            )
            TransactionType.CLOSE_POSITION -> listOf(
                CreateTransactionTO.ClosePosition(
                    dateTime = transaction.dateTime,
                    externalId = transaction.transactionExternalId,
                    currencyRecord = currencyRecordTO,
                    instrumentRecord = instrumentRecordTO
                )
            )
            else -> throw ImportDataException("Invalid transaction type for investment: $transactionType")
        }
    }
}
