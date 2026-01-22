package ro.jf.funds.importer.service.service.conversion.strategy

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.getRequiredImportConversions
import ro.jf.funds.importer.service.service.conversion.toFundRecordAmount

class ExchangeSingleTransactionConverter : ImportTransactionConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean {
        if (transaction.records.size !in 2..3) {
            return false
        }
        val targetUnits = transaction.records.map { accountStore[it.accountName] }.map { it.unit }.distinct()
        if (targetUnits.size != 2 || targetUnits.any { it !is Currency }) {
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
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion> {
        val importConversions = transaction.getRequiredImportConversions(accountStore)
        val targetCurrency = transaction.records
            .filter { it.amount > BigDecimal.ZERO }
            .map { accountStore[it.accountName].unit }
            .first() as? Currency ?: throw ImportDataException("Invalid target currency")
        val sourceCurrency = transaction.records
            .map { accountStore[it.accountName].unit }
            .first { it != targetCurrency } as? Currency ?: throw ImportDataException("Invalid source currency")
        return importConversions +
                Conversion(transaction.dateTime.date, sourceCurrency, targetCurrency) +
                Conversion(transaction.dateTime.date, targetCurrency, sourceCurrency)
    }

    override fun mapToTransactions(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): List<CreateTransactionTO> {
        val date = transaction.dateTime.date

        val creditRecord = transaction.records.single { it.amount > BigDecimal.ZERO }
        val creditAmount = creditRecord
            .toFundRecordAmount(date, accountStore[creditRecord.accountName], conversions)
        val creditFundRecord = CreateTransactionRecordTO.CurrencyRecord(
            fundId = fundStore[creditRecord.fundName].id,
            accountId = accountStore[creditRecord.accountName].id,
            amount = creditAmount,
            unit = creditRecord.unit as Currency,
            labels = creditRecord.labels,
        )

        val (debitRecord, debitTotalAmount) = transaction.records
            .asSequence()
            .filter { it.amount < BigDecimal.ZERO }
            .map { it to it.toFundRecordAmount(date, accountStore[it.accountName], conversions) }
            .sortedByDescending { (_, amount) -> (creditAmount + amount).abs() }
            .first()
        val rate = conversions.getConversionRate(creditRecord.unit, debitRecord.unit, date)

        val debitAmount = creditAmount.negate() * rate
        val debitFundRecord = CreateTransactionRecordTO.CurrencyRecord(
            fundId = fundStore[debitRecord.fundName].id,
            accountId = accountStore[debitRecord.accountName].id,
            amount = debitAmount,
            unit = debitRecord.unit as Currency,
            labels = debitRecord.labels,
        )

        val feeRecord = transaction.records.singleOrNull { it != debitRecord && it != creditRecord }
        val feeAmount = debitTotalAmount - debitAmount
        val feeFundRecord = (debitTotalAmount - debitAmount)
            .takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
            ?.let {
                CreateTransactionRecordTO.CurrencyRecord(
                    fundId = fundStore[debitRecord.fundName].id,
                    accountId = accountStore[debitRecord.accountName].id,
                    amount = feeAmount,
                    unit = debitRecord.unit as Currency,
                    labels = feeRecord?.labels ?: debitRecord.labels,
                )
            }

        return listOf(
            CreateTransactionTO.Exchange(
                dateTime = transaction.dateTime,
                externalId = transaction.transactionExternalId,
                sourceRecord = debitFundRecord,
                destinationRecord = creditFundRecord,
                feeRecord = feeFundRecord
            )
        )
    }

    private fun ConversionsResponse.getConversionRate(sourceUnit: FinancialUnit, targetUnit: FinancialUnit, date: LocalDate): BigDecimal {
        val targetCurrency = targetUnit as? Currency
            ?: throw ImportDataException("Unit $targetUnit is not a currency, conversion would not be supported.")
        if (sourceUnit == targetCurrency) return BigDecimal.ONE
        return getRate(sourceUnit, targetCurrency, date)
            ?: throw ImportDataException("Conversions from $sourceUnit to $targetCurrency on $date not available.")
    }
}
