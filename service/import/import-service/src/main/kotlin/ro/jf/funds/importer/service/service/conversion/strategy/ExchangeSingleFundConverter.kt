package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.*
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.*
import java.math.BigDecimal
import java.util.*

class ExchangeSingleFundConverter : ImportFundConverter {
    override fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean {
        if (transaction.records.size !in 2..3) {
            return false
        }
        val targetUnits = transaction.records.map { it.resolveAccount() }.map { it.unit }.distinct()
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
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): List<ConversionRequest> {
        val importConversions = transaction.getRequiredImportConversions { resolveAccount() }
        val targetCurrency = transaction.records
            .filter { it.amount > BigDecimal.ZERO }
            .map { it.resolveAccount().unit }
            .first() as? Currency ?: throw ImportDataException("Invalid target currency")
        val sourceCurrency = transaction.records
            .map { it.resolveAccount().unit }
            .first { it != targetCurrency } as? Currency ?: throw ImportDataException("Invalid source currency")
        return importConversions +
                ConversionRequest(transaction.dateTime.date, sourceCurrency to targetCurrency) +
                ConversionRequest(transaction.dateTime.date, targetCurrency to sourceCurrency)
    }

    override fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        resolveFundId: ImportParsedRecord.() -> UUID,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
        currencyConverter: ConversionContext,
    ): ImportFundTransaction {
        val date = transaction.dateTime.date

        val creditRecord = transaction.records.single { it.amount > BigDecimal.ZERO }
        val creditAmount = creditRecord
            .toFundRecordAmount(date, creditRecord.resolveAccount(), currencyConverter)
        val creditFundRecord = ImportFundRecord(
            fundId = creditRecord.resolveFundId(),
            accountId = creditRecord.resolveAccount().id,
            amount = creditAmount,
            unit = creditRecord.unit,
        )

        val (debitRecord, debitTotalAmount) = transaction.records
            .asSequence()
            .filter { it.amount < BigDecimal.ZERO }
            .map { it to it.toFundRecordAmount(date, it.resolveAccount(), currencyConverter) }
            .sortedByDescending { (_, amount) -> (creditAmount + amount).abs() }
            .first()
        val debitAmount = creditAmount.negate() *
                currencyConverter.getRate(date, creditRecord.unit as Currency, debitRecord.unit as Currency)
        val debitFundRecord = ImportFundRecord(
            fundId = debitRecord.resolveFundId(),
            accountId = debitRecord.resolveAccount().id,
            amount = debitAmount,
            unit = debitRecord.unit,
        )

        val feeAmount = debitTotalAmount - debitAmount
        val feeFundRecord = (debitTotalAmount - debitAmount)
            .takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
            .let {
                ImportFundRecord(
                    fundId = debitRecord.resolveFundId(),
                    accountId = debitRecord.resolveAccount().id,
                    amount = feeAmount,
                    unit = debitRecord.unit,
                )
            }

        return ImportFundTransaction(
            dateTime = transaction.dateTime,
            type = ImportFundTransaction.Type.EXCHANGE,
            records = listOfNotNull(creditFundRecord, debitFundRecord, feeFundRecord)
        )
    }
}
