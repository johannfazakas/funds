package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.getRequiredImportConversions
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord
import java.math.BigDecimal

class TransferTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size != 2) {
            return false
        }
        val sourceUnits = transaction.records.map { it.unit }
        if (sourceUnits[0] != sourceUnits[1] || sourceUnits.any { it !is Currency }) {
            return false
        }
        val targetUnits = transaction.records.map { accountStore[it.accountName] }.map { it.unit }
        if (targetUnits[0] != targetUnits[1] || targetUnits.any { it !is Currency }) {
            return false
        }
        return transaction.records.sumOf { it.amount }.compareTo(BigDecimal.ZERO) == 0
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion> {
        return transaction.getRequiredImportConversions(accountStore)
    }

    override fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): CreateFundTransactionTO {
        return CreateFundTransactionTO(
            dateTime = transaction.dateTime,
            externalId = transaction.transactionExternalId,
            records = transaction.records.map { record ->
                record.toImportCurrencyFundRecord(
                    transaction.dateTime.date,
                    fundStore[record.fundName].id,
                    accountStore[record.accountName],
                    conversions
                )
            }
        )
    }
}
