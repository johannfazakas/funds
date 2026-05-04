package ro.jf.funds.importer.service.service.conversion.strategy

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateTransactionRecordTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
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
        accountStore: Store<AccountTO>,
    ): Boolean {
        if (transaction.records.size !in 2..3) {
            return false
        }
        val targetUnits = transaction.records.map { accountStore[it.accountId] }.map { it.unit }.distinct()
        if (targetUnits.size != 2 || targetUnits.any { it !is Currency }) {
            return false
        }
        val positiveRecords = transaction.records.filter { it.amount > BigDecimal.ZERO }
        if (positiveRecords.size != 1) {
            return false
        }
        val accounts = transaction.records.map { it.accountId }.distinct()
        return accounts.size == 2
    }

    override fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): List<Conversion> {
        val importConversions = transaction.getRequiredImportConversions(accountStore)
        val targetCurrency = transaction.records
            .filter { it.amount > BigDecimal.ZERO }
            .map { accountStore[it.accountId].unit }
            .first() as? Currency ?: throw ImportDataException("Invalid target currency: $transaction")
        val sourceCurrency = transaction.records
            .map { accountStore[it.accountId].unit }
            .first { it != targetCurrency } as? Currency ?: throw ImportDataException("Invalid source currency: $transaction")
        return importConversions +
                Conversion(transaction.dateTime.date, sourceCurrency, targetCurrency) +
                Conversion(transaction.dateTime.date, targetCurrency, sourceCurrency)
    }

    override fun mapToTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        accountStore: Store<AccountTO>,
    ): CreateTransactionTO {
        val date = transaction.dateTime.date

        val creditRecord = transaction.records.single { it.amount > BigDecimal.ZERO }
        val creditAccount = accountStore[creditRecord.accountId]
        val creditAmount = creditRecord.toFundRecordAmount(date, creditAccount, conversions)
        val creditFundRecord = CreateTransactionRecordTO.CurrencyRecord(
            fundId = creditRecord.fundId,
            accountId = creditRecord.accountId,
            amount = creditAmount,
            unit = creditRecord.unit as Currency,
            categoryId = creditRecord.categoryId,
            note = creditRecord.note,
        )

        val (debitRecord, debitTotalAmount) = transaction.records
            .asSequence()
            .filter { it.amount < BigDecimal.ZERO }
            .map { it to it.toFundRecordAmount(date, accountStore[it.accountId], conversions) }
            .sortedByDescending { (_, amount) -> (creditAmount + amount).abs() }
            .first()
        val rate = conversions.getConversionRate(creditRecord.unit, debitRecord.unit, date, transaction)

        val debitAmount = creditAmount.negate() * rate
        val debitFundRecord = CreateTransactionRecordTO.CurrencyRecord(
            fundId = debitRecord.fundId,
            accountId = debitRecord.accountId,
            amount = debitAmount,
            unit = debitRecord.unit as Currency,
            categoryId = debitRecord.categoryId,
            note = debitRecord.note,
        )

        val feeRecord = transaction.records.singleOrNull { it != debitRecord && it != creditRecord }
        val feeAmount = debitTotalAmount - debitAmount
        val feeFundRecord = (debitTotalAmount - debitAmount)
            .takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
            ?.let {
                CreateTransactionRecordTO.CurrencyRecord(
                    fundId = debitRecord.fundId,
                    accountId = debitRecord.accountId,
                    amount = feeAmount,
                    unit = debitRecord.unit as Currency,
                    categoryId = feeRecord?.categoryId ?: debitRecord.categoryId,
                    note = feeRecord?.note ?: debitRecord.note,
                )
            }

        return CreateTransactionTO.Exchange(
            dateTime = transaction.dateTime,
            externalId = transaction.transactionExternalId,
            sourceRecord = debitFundRecord,
            destinationRecord = creditFundRecord,
            feeRecord = feeFundRecord
        )
    }

    private fun ConversionsResponse.getConversionRate(sourceUnit: FinancialUnit, targetUnit: FinancialUnit, date: LocalDate, transaction: ImportParsedTransaction): BigDecimal {
        val targetCurrency = targetUnit as? Currency
            ?: throw ImportDataException("Unit $targetUnit is not a currency, conversion would not be supported: $transaction")
        if (sourceUnit == targetCurrency) return BigDecimal.ONE
        return getRate(sourceUnit, targetCurrency, date)
            ?: throw ImportDataException("Conversions from $sourceUnit to $targetCurrency on $date not available: $transaction")
    }
}
