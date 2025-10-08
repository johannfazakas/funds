package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord
import java.math.BigDecimal

class InvestmentTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size != 2) return false
        val currencyRecords = transaction.records.filter { it.unit is Currency }
        if (currencyRecords.size != 1) return false
        val instrumentRecords = transaction.records.filter { it.unit is Symbol }
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
            listOf(
                Conversion(
                    transaction.dateTime.date,
                    currencyRecord.unit,
                    currencyAccount.unit
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
        val instrumentRecord = transaction.records.first { it.unit is Symbol }

        val transactionType = when {
            currencyRecord.amount < BigDecimal.ZERO && instrumentRecord.amount > BigDecimal.ZERO -> TransactionType.OPEN_POSITION
            currencyRecord.amount > BigDecimal.ZERO && instrumentRecord.amount < BigDecimal.ZERO -> TransactionType.CLOSE_POSITION
            else -> throw ImportDataException("Invalid investment transaction: currency and instrument amounts must have opposite signs")
        }

        return listOf(
            CreateTransactionTO(
                dateTime = transaction.dateTime,
                externalId = transaction.transactionExternalId,
                type = transactionType,
                records = listOf(
                    currencyRecord.toImportCurrencyFundRecord(
                        date = transaction.dateTime.date,
                        fundId = fundStore[currencyRecord.fundName].id,
                        account = accountStore[currencyRecord.accountName],
                        conversions = conversions,
                    ),
                    CreateTransactionRecord(
                        fundId = fundStore[instrumentRecord.fundName].id,
                        accountId = accountStore[instrumentRecord.accountName].id,
                        amount = instrumentRecord.amount,
                        unit = instrumentRecord.unit,
                        labels = instrumentRecord.labels,
                    )
                )
            )
        )
    }
}
