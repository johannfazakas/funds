package ro.jf.funds.importer.service.service.conversion.converter

import kotlinx.datetime.LocalDate
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.*
import java.math.BigDecimal
import java.util.*

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

fun ImportParsedRecord.toImportCurrencyFundRecord(
    date: LocalDate,
    fundId: UUID,
    account: AccountTO,
    resolveConversionRate: ConversionRequest.() -> BigDecimal
): ImportFundRecord {
    return ImportFundRecord(
        fundId = fundId,
        accountId = account.id,
        amount = if (unit == account.unit) {
            amount
        } else {
            val conversionRate = ConversionRequest(
                date,
                CurrencyPair(unit as Currency, account.unit as Currency)
            ).resolveConversionRate()
            conversionRate * amount
        },
        unit = account.unit as Currency,
    )
}
