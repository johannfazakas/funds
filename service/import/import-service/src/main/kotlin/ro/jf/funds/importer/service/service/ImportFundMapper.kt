package ro.jf.funds.importer.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.service.domain.ImportRecord
import ro.jf.funds.importer.service.domain.ImportTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.util.*

private val log = logger { }

class ImportFundMapper(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk,
    private val fundTransactionSdk: FundTransactionSdk
) {
    suspend fun mapToFundTransactions(
        userId: UUID,
        importTransactions: List<ImportTransaction>
    ): List<CreateFundTransactionTO> {
        log.info { "Handling import >> user = $userId items size = ${importTransactions.size}." }

        val transactionRequests = importTransactions
            .toTransactionRequests(createImportResourceContext(userId))
        val transactions = CreateFundTransactionsTO(transactionRequests)
        fundTransactionSdk.createTransactions(userId, transactions)
        return transactionRequests
    }

    private fun List<ImportTransaction>.toTransactionRequests(importResourceContext: ImportResourceContext): List<CreateFundTransactionTO> =
        map { it.toTransactionRequest(importResourceContext) }

    private fun ImportTransaction.toTransactionRequest(importResourceContext: ImportResourceContext) =
        CreateFundTransactionTO(
            dateTime = dateTime,
            records = records.map { record ->
                record.toRecordRequest(importResourceContext)
            }
        )

    private fun ImportRecord.toRecordRequest(importResourceContext: ImportResourceContext) =
        CreateFundRecordTO(
            fundId = importResourceContext.getFundId(fundName),
            accountId = importResourceContext.getAccountId(accountName),
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
        private val accountIdByName: Map<AccountName, UUID> = accounts.associate { it.name to it.id }
        private val fundIdByName: Map<FundName, UUID> = funds.associate { it.name to it.id }

        fun getAccountId(accountName: AccountName) = accountIdByName[accountName]
            ?: throw ImportDataException("Record account not found: $accountName")

        fun getFundId(fundName: FundName) = fundIdByName[fundName]
            ?: throw ImportDataException("Record fund not found: $fundName")
    }
}
