package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
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
        if (currencyRecords.size != 1 || currencyRecords[0].amount >= BigDecimal.ZERO) return false
        val instrumentRecords = transaction.records.filter { it.unit is Symbol }
        if (instrumentRecords.size != 1 || instrumentRecords[0].amount <= BigDecimal.ZERO) return false
        val instrumentRecord = instrumentRecords.first()
        if (instrumentRecord.unit != accountStore[instrumentRecord.accountName].unit) return false
        return true
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion> {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val currencyAccount = accountStore[currencyRecord.accountName]

        return if (currencyRecord.unit != currencyAccount) {
            listOf(
                Conversion(
                    transaction.dateTime.date,
                    currencyRecord.unit,
                    currencyRecord.unit
                )
            )
        } else {
            listOf()
        }
    }

    override fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): CreateFundTransactionTO {
        val currencyRecord = transaction.records.first { it.unit is Currency }
        val instrumentRecord = transaction.records.first { it.unit is Symbol }

        return CreateFundTransactionTO(
            dateTime = transaction.dateTime,
            externalId = transaction.transactionExternalId,
            records = listOf(
                currencyRecord.toImportCurrencyFundRecord(
                    date = transaction.dateTime.date,
                    fundId = fundStore[currencyRecord.fundName].id,
                    account = accountStore[currencyRecord.accountName],
                    conversions = conversions,
                ),
                CreateFundRecordTO(
                    fundId = fundStore[instrumentRecord.fundName].id,
                    accountId = accountStore[instrumentRecord.accountName].id,
                    amount = instrumentRecord.amount,
                    unit = instrumentRecord.unit,
                    labels = instrumentRecord.labels,
                )
            )
        )
    }
}