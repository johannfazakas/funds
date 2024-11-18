package ro.jf.funds.importer.service.service

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.*
import ro.jf.funds.historicalpricing.api.model.Currency as HPCurrency

private val log = logger { }

class ImportFundMapper(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk,
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun mapToFundRequest(
        userId: UUID,
        parsedTransactions: List<ImportParsedTransaction>
    ): CreateFundTransactionsTO {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }

        val transactionRequests = parsedTransactions
            .toFundTransactions(createImportResourceContext(userId))
        return transactionRequests.toRequest()
    }

    private fun List<ImportParsedTransaction>.toFundTransactions(importResourceContext: ImportResourceContext): List<ImportFundTransaction> =
        map { it.toTransactionRequest(importResourceContext) }

    private fun ImportParsedTransaction.toTransactionRequest(importResourceContext: ImportResourceContext): ImportFundTransaction =
        when {
            isSimpleTransactionRequest(importResourceContext) -> {
                toSimpleTransactionRequest(importResourceContext)
            }

            else -> {
                throw ImportDataException("Unrecognized transaction type: $this")
            }
        }

    private fun ImportParsedTransaction.toSimpleTransactionRequest(importResourceContext: ImportResourceContext): ImportFundTransaction =
        // TODO will have to handle currency transformations
        ImportFundTransaction(
            dateTime = dateTime,
            // TODO(Johann) not exactly
            type = ImportFundTransaction.Type.SINGLE_RECORD,
            records = records.map { record ->
                record.toRecordRequest(importResourceContext)
            }
        )

    // TODO(Johann) all this currency exchange should be extracted to some historical pricing adapter maybe
    // TODO(Johann) should also have some historical pricing local caching
    private fun ImportParsedRecord.amountInEur(date: LocalDate): BigDecimal {
        return when (unit) {
            is Currency -> when (unit) {
                Currency.EUR -> amount
                else -> historicalPricingSdk.convertCurrency(
                    sourceCurrency = unit.toHistoricalPricingCurrency(),
                    targetCurrency = HPCurrency.EUR,
                    date = date
                ).price * amount
            }

            is Symbol -> throw ImportDataException("Instrument symbols not supported yet")
        }
    }

    private fun Currency.toHistoricalPricingCurrency(): HPCurrency {
        return when (this) {
            Currency.RON -> HPCurrency.RON
            Currency.EUR -> HPCurrency.EUR
            else -> throw ImportDataException("Currency not supported: $this")
        }
    }

    private fun ImportParsedTransaction.isCurrencyExchangeTransaction(importResourceContext: ImportResourceContext): Boolean {
        val accounts = records.map { it.accountName }.toSet()
        val financialUnits = records.map { importResourceContext.getAccount(it.accountName).unit }
        return financialUnits.all { it is Currency } && financialUnits.toSet().size == 2 && accounts.size == 2 && records.size <= 3
    }

    private fun ImportParsedTransaction.isSimpleTransactionRequest(importResourceContext: ImportResourceContext): Boolean {
        val financialUnits = records.map { importResourceContext.getAccount(it.accountName).unit }
        return financialUnits.all { it is Currency } && financialUnits.toSet().size == 1
    }

    private fun ImportParsedRecord.toRecordRequest(importResourceContext: ImportResourceContext) =
        ImportFundRecord(
            fundId = importResourceContext.getFundId(fundName),
            accountId = importResourceContext.getAccount(accountName).id,
            amount = amount,
            // TODO(Johann) not actually, should revisit
            unit = Currency.RON,
        )

    private suspend fun createImportResourceContext(userId: UUID) = ImportResourceContext(
        accountSdk.listAccounts(userId).items,
        fundSdk.listFunds(userId).items
    )

    private class ImportResourceContext(
        accounts: List<AccountTO>,
        funds: List<FundTO>
    ) {
        private val accountByName: Map<AccountName, AccountTO> = accounts.associateBy { it.name }
        private val fundIdByName: Map<FundName, UUID> = funds.associate { it.name to it.id }

        fun getAccount(accountName: AccountName) = accountByName[accountName]
            ?: throw ImportDataException("Record account not found: $accountName")

        fun getFundId(fundName: FundName) = fundIdByName[fundName]
            ?: throw ImportDataException("Record fund not found: $fundName")
    }
}
