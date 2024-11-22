package ro.jf.funds.importer.service.service.conversion.converter

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.CurrencyPair

fun ImportParsedTransaction.getRequiredImportConversions(
    resolveAccount: ImportParsedRecord.() -> AccountTO
): List<ConversionRequest> = records
    .mapNotNull {
        val sourceCurrency = it.unit as? Currency ?: return@mapNotNull null
        val targetCurrency = it.resolveAccount().unit as? Currency ?: return@mapNotNull null
        if (sourceCurrency == targetCurrency) return@mapNotNull null
        ConversionRequest(
            date = dateTime.date,
            currencyPair = CurrencyPair(
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency
            )
        )
    }
