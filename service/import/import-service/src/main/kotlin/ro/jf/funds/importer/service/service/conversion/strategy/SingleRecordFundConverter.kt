package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import ro.jf.funds.importer.service.service.conversion.ImportFundConverter
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction
import ro.jf.funds.importer.service.service.conversion.getRequiredImportConversions
import ro.jf.funds.importer.service.service.conversion.toImportCurrencyFundRecord
import java.math.BigDecimal
import java.util.*

class SingleRecordFundConverter : ImportFundConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean {
        if (transaction.records.size != 1) {
            return false
        }
        val singleRecord = transaction.records.first()
        return singleRecord.unit is Currency && singleRecord.resolveAccount().unit is Currency
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): List<ConversionRequest> {
        return transaction.getRequiredImportConversions { resolveAccount() }
    }

    override fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        resolveFundId: ImportParsedRecord.() -> UUID,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
        resolveConversionRate: ConversionRequest.() -> BigDecimal,
    ): ImportFundTransaction {
        return ImportFundTransaction(
            dateTime = transaction.dateTime,
            type = ImportFundTransaction.Type.SINGLE_RECORD,
            records = transaction.records.map { record ->
                record.toImportCurrencyFundRecord(
                    transaction.dateTime.date,
                    record.resolveFundId(),
                    record.resolveAccount()
                ) { resolveConversionRate() }
            }
        )
    }
}
