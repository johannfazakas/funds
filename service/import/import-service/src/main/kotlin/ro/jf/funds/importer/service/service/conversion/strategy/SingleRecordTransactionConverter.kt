package ro.jf.funds.importer.service.service.conversion.strategy

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.getRequiredImportConversions
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord

class SingleRecordTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): Boolean {
        if (transaction.records.size != 1) {
            return false
        }
        val singleRecord = transaction.records.first()
        return singleRecord.unit is Currency && accountStore[singleRecord.accountId].unit is Currency
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): List<Conversion> {
        return transaction.getRequiredImportConversions(accountStore)
    }

    override fun mapToTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        accountStore: Store<AccountTO>,
        categoryStore: Store<CategoryTO>,
    ): CreateTransactionTO {
        val record = transaction.records.first()
        return CreateTransactionTO.SingleRecord(
            dateTime = transaction.dateTime,
            externalId = transaction.transactionExternalId,
            record = record.toImportCurrencyFundRecord(
                transaction.dateTime.date,
                accountStore[record.accountId],
                conversions,
                categoryStore,
            )
        )
    }
}
