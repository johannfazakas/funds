package ro.jf.funds.importer.service.service.conversion

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.platform.api.model.Currency

fun ImportParsedTransaction.getRequiredImportConversions(
    accountStore: Store<AccountTO>,
): List<Conversion> = records
    .mapNotNull {
        val sourceCurrency = it.unit as? Currency ?: return@mapNotNull null
        val targetCurrency = accountStore[it.accountId].unit as? Currency ?: return@mapNotNull null
        if (sourceCurrency == targetCurrency) return@mapNotNull null
        Conversion(dateTime.date, sourceCurrency, targetCurrency)
    }

fun ImportParsedRecord.toImportCurrencyFundRecord(
    date: LocalDate,
    account: AccountTO,
    conversions: ConversionsResponse,
): CreateTransactionRecordTO.CurrencyRecord {
    return CreateTransactionRecordTO.CurrencyRecord(
        fundId = fundId,
        accountId = account.id,
        amount = toFundRecordAmount(date, account, conversions),
        unit = account.unit as Currency,
        categoryId = categoryId,
        note = note,
    )
}

fun ImportParsedRecord.toFundRecordAmount(
    date: LocalDate,
    account: AccountTO,
    conversions: ConversionsResponse,
): BigDecimal {
    return if (unit == account.unit) {
        amount
    } else {
        val targetCurrency = account.unit as? Currency
            ?: throw ImportDataException("Unit ${account.unit} is not a currency, conversion would not be supported: $this")
        val rate = conversions.getRate(unit, targetCurrency, date)
            ?: throw ImportDataException("Conversions from $unit to $targetCurrency on $date not available: $this")
        amount * rate
    }
}
