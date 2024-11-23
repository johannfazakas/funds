package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.service.conversion.ImportFundConverter
import ro.jf.funds.importer.service.service.conversion.getRequiredImportConversions
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord
import java.math.BigDecimal

class ImplicitTransferFundConverter : ImportFundConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size != 3) {
            return false
        }
        val sourceUnits = transaction.records.map { it.unit }.distinct()
        if (sourceUnits.size != 1 || sourceUnits.any { it !is Currency }) {
            return false
        }
        val targetUnits = transaction.records.map { accountStore[it.accountName].unit }.distinct()
        if (targetUnits.size != 1 || targetUnits.any { it !is Currency }) {
            return false
        }
        val accounts = transaction.records.map { it.accountName }.distinct()
        if (accounts.size != 1) {
            return false
        }
        val recordsByFund = transaction.records.groupBy { it.fundName }
        if (recordsByFund.size != 2) {
            return false
        }
        val passThroughRecords = recordsByFund.values.first { it.size == 2 }
        val principalRecord = recordsByFund.values.first { it.size == 1 }
        return passThroughRecords.sumOf { it.amount }.compareTo(BigDecimal.ZERO) == 0 &&
                passThroughRecords.any { it.amount.compareTo(principalRecord.first().amount) == 0 }
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion> {
        return transaction.getRequiredImportConversions(accountStore)
    }

    override fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
        conversionRateStore: Store<Conversion, BigDecimal>,
    ): CreateFundTransactionTO {
        return CreateFundTransactionTO(
            dateTime = transaction.dateTime,
            records = transaction.records.map { record ->
                record.toImportCurrencyFundRecord(
                    transaction.dateTime.date,
                    fundStore[record.fundName].id,
                    accountStore[record.accountName],
                    conversionRateStore
                )
            }
        )
    }
}