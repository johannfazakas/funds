package ro.jf.funds.importer.service.service.conversion.converter

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction.Type.IMPLICIT_TRANSFER
import java.math.BigDecimal
import java.util.*

class ImplicitTransferFundConverter : ImportFundConverter {
    override fun getType() = ImportFundTransaction.Type.TRANSFER

    override fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean {
        if (transaction.records.size != 3) {
            return false
        }
        val sourceUnits = transaction.records.map { it.unit }.distinct()
        if (sourceUnits.size != 1 || sourceUnits.any { it !is Currency }) {
            return false
        }
        val targetUnits = transaction.records.map { it.resolveAccount().unit }.distinct()
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
            type = IMPLICIT_TRANSFER,
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