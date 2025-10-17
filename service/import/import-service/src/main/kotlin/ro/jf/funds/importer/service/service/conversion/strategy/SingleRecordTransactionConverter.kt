package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.*
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

    override fun mapToTransactions(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): List<CreateTransactionTO> {
        val record = transaction.records.first()
        return listOf(
            CreateTransactionTO.SingleRecord(
                dateTime = transaction.dateTime,
                externalId = transaction.transactionExternalId,
                record = record.toImportCurrencyFundRecord(
                    transaction.dateTime.date,
                    fundStore[record.fundName].id,
                    accountStore[record.accountName],
                    conversions,
                )
            )
        )
    }
}
