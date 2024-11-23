package ro.jf.funds.importer.service.service.conversion

import kotlinx.datetime.LocalDate
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ImportFundRecord
import java.math.BigDecimal
import java.util.*

fun ImportParsedTransaction.getRequiredImportConversions(
    resolveAccount: ImportParsedRecord.() -> AccountTO,
): List<Conversion> = records
    .mapNotNull {
        val sourceCurrency = it.unit as? Currency ?: return@mapNotNull null
        val targetCurrency = it.resolveAccount().unit as? Currency ?: return@mapNotNull null
        if (sourceCurrency == targetCurrency) return@mapNotNull null
        Conversion(dateTime.date, sourceCurrency, targetCurrency)
    }

fun ImportParsedRecord.toImportCurrencyFundRecord(
    date: LocalDate,
    fundId: UUID,
    account: AccountTO,
    conversionRateStore: Store<Conversion, BigDecimal>,
): ImportFundRecord {
    return ImportFundRecord(
        fundId = fundId,
        accountId = account.id,
        amount = toFundRecordAmount(date, account, conversionRateStore),
        unit = account.unit as Currency,
    )
}

fun ImportParsedRecord.toFundRecordAmount(
    date: LocalDate,
    account: AccountTO,
    conversionRateStore: Store<Conversion, BigDecimal>,
): BigDecimal {
    return if (unit == account.unit) {
        amount
    } else {
        amount * conversionRateStore[Conversion(date, unit, account.unit)]
    }
}