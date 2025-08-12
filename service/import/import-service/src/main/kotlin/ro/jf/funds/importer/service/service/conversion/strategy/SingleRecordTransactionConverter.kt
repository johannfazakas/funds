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

class SingleRecordTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size != 1) {
            return false
        }
        val singleRecord = transaction.records.first()
        return singleRecord.unit is Currency && accountStore[singleRecord.accountName].unit is Currency
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
                    conversions,
                )
            }
        )
    }
}
