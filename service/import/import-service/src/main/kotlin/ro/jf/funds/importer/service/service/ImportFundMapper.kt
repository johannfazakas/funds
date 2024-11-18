package ro.jf.funds.importer.service.service

import kotlinx.datetime.LocalDateTime
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
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

    private fun ImportParsedTransaction.toTransactionRequest(importResourceContext: ImportResourceContext): ImportFundTransaction {
        val type = ImportFundTransaction.Type.entries.firstOrNull {
            it.matcher(importResourceContext)(this)
        } ?: throw ImportDataException("Unrecognized transaction type: $this")
        return when (type) {
            ImportFundTransaction.Type.SINGLE_RECORD -> toSingleRecordFundTransaction(importResourceContext)
            ImportFundTransaction.Type.TRANSFER -> toTransferFundTransaction(importResourceContext)
        }
    }

    private fun ImportFundTransaction.Type.matcher(importResourceContext: ImportResourceContext): (ImportParsedTransaction) -> Boolean {
        return when (this) {
            ImportFundTransaction.Type.SINGLE_RECORD -> { transaction ->
                transaction.records.size == 1 && transaction.records.first()
                    .let { importResourceContext.getAccount(it.accountName).unit is Currency }
            }

            ImportFundTransaction.Type.TRANSFER -> { transaction ->
                val financialUnits = transaction.records
                    .map { importResourceContext.getAccount(it.accountName) }
                    .map { it.unit }
                    .toSet()
                // TODO(Johann) should also check if the amounts are opposite
                transaction.records.size == 2 && financialUnits.size == 1
            }
        }
    }

    private fun ImportParsedTransaction.toSingleRecordFundTransaction(
        importResourceContext: ImportResourceContext
    ): ImportFundTransaction {
        return ImportFundTransaction(
            dateTime = dateTime,
            type = ImportFundTransaction.Type.SINGLE_RECORD,
            records = records.map { record ->
                record.toImportCurrencyFundRecord(importResourceContext)
            }
        )
    }

    private fun ImportParsedTransaction.toTransferFundTransaction(
        importResourceContext: ImportResourceContext
    ): ImportFundTransaction {
        return ImportFundTransaction(
            dateTime = dateTime,
            type = ImportFundTransaction.Type.TRANSFER,
            records = records.map { record ->
                record.toImportCurrencyFundRecord(importResourceContext)
            }
        )
    }

    private fun ImportParsedRecord.toImportCurrencyFundRecord(
        importResourceContext: ImportResourceContext
    ): ImportFundRecord {
        val account = importResourceContext.getAccount(accountName)
        return ImportFundRecord(
            fundId = importResourceContext.getFundId(fundName),
            accountId = account.id,
            amount = if (unit == account.unit) {
                amount
            } else {
                throw ImportDataException("Currency conversion not supported yet for single record fund transaction")
            },
            unit = importResourceContext.getAccount(accountName).unit as Currency,
        )
    }

    // TODO(Johann) all this currency exchange should be extracted to some historical pricing adapter maybe
    // TODO(Johann) should also have some historical pricing local caching

    private fun Currency.toHistoricalPricingCurrency(): HPCurrency {
        return when (this) {
            Currency.RON -> HPCurrency.RON
            Currency.EUR -> HPCurrency.EUR
            else -> throw ImportDataException("Currency not supported: $this")
        }
    }

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


    fun List<ImportFundTransaction>.toRequest(): CreateFundTransactionsTO =
        CreateFundTransactionsTO(map { it.toRequest() })

    data class ImportFundTransaction(
        val dateTime: LocalDateTime,
        val type: Type,
        val records: List<ImportFundRecord>
    ) {
        enum class Type {
            SINGLE_RECORD,
            TRANSFER,
            // TODO(Johann) add EXCHANGE type
        }

        fun toRequest(): CreateFundTransactionTO {
            return CreateFundTransactionTO(
                dateTime = dateTime,
                records = records.map { it.toRequest() }
            )
        }
    }

    data class ImportFundRecord(
        val fundId: UUID,
        val accountId: UUID,
        val amount: BigDecimal,
        val unit: FinancialUnit
    ) {
        fun toRequest(): CreateFundRecordTO {
            return CreateFundRecordTO(
                fundId = fundId,
                accountId = accountId,
                amount = amount,
                unit = unit
            )
        }
    }
}
