package ro.jf.funds.importer.service.service.conversion

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.*

fun ImportParsedTransaction.getRequiredImportConversions(
    accountStore: Store<AccountName, AccountTO>,
): List<Conversion> = records
    .mapNotNull {
        val sourceCurrency = it.unit as? Currency ?: return@mapNotNull null
        val targetCurrency = accountStore[it.accountName].unit as? Currency ?: return@mapNotNull null
        if (sourceCurrency == targetCurrency) return@mapNotNull null
        Conversion(dateTime.date, sourceCurrency, targetCurrency)
    }

fun ImportParsedRecord.toImportCurrencyFundRecord(
    date: LocalDate,
    fundId: UUID,
    account: AccountTO,
    conversions: ConversionsResponse,
): CreateTransactionRecordTO {
    return CreateTransactionRecordTO(
        fundId = fundId,
        accountId = account.id,
        amount = toFundRecordAmount(date, account, conversions),
        unit = account.unit as Currency,
        labels = labels,
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
            ?: throw ImportDataException("Unit ${account.unit} is not a currency, conversion would not be supported.")
        val rate = conversions.getRate(unit, targetCurrency, date)
        amount * rate
    }
}
