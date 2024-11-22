package ro.jf.funds.importer.service.service.conversion.converter

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.CurrencyPair
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction
import java.math.BigDecimal

class ExchangeSingleFundConverter : ImportFundConverter {
    override fun getType() = ImportFundTransaction.Type.EXCHANGE

    override fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO
    ): Boolean {
        if (transaction.records.size !in 2..3) {
            return false
        }
        val sourceUnits = transaction.records.map { it.unit }.distinct()
        if (sourceUnits.size != 1 || sourceUnits.any { it !is Currency }) {
            return false
        }
        val targetUnits = transaction.records.map { it.resolveAccount() }.map { it.unit }.distinct()
        if (targetUnits.size != 1 || targetUnits.any { it !is Currency }) {
            return false
        }
        val positiveRecords = transaction.records.filter { it.amount > BigDecimal.ZERO }
        if (positiveRecords.size != 1) {
            return false
        }
        val accounts = transaction.records.map { it.accountName }.distinct()
        return accounts.size == 2
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO
    ): List<ConversionRequest> {
        val importConversions = transaction.getRequiredImportConversions { resolveAccount() }
        val targetCurrency = transaction.records
            .filter { it.amount > BigDecimal.ZERO }
            .map { it.resolveAccount().unit }
            .first() as? Currency ?: ImportDataException("Invalid target unit not currency type")
        val sourceCurrency = transaction.records
            .map { it.resolveAccount().unit }
            .first { it != targetCurrency } as? Currency ?: ImportDataException("Invalid source unit not currency type")
        val conversionRequest = ConversionRequest(
            date = transaction.dateTime.date,
            currencyPair = CurrencyPair(sourceCurrency as Currency, targetCurrency as Currency)
        )
        return importConversions + conversionRequest
    }
}
