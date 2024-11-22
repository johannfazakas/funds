package ro.jf.funds.importer.service.service.conversion.converter

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction.Type.TRANSFER
import java.math.BigDecimal
import java.util.*

class TransferFundConverter : ImportFundConverter {
    override fun getType() = ImportFundTransaction.Type.TRANSFER

    override fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean {
        if (transaction.records.size != 2) {
            return false
        }
        val sourceUnits = transaction.records.map { it.unit }
        if (sourceUnits[0] != sourceUnits[1] || sourceUnits.any { it !is Currency }) {
            return false
        }
        val targetUnits = transaction.records.map { it.resolveAccount() }.map { it.unit }
        if (targetUnits[0] != targetUnits[1] || targetUnits.any { it !is Currency }) {
            return false
        }
        return transaction.records.sumOf { it.amount }.compareTo(BigDecimal.ZERO) == 0
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
            type = TRANSFER,
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
