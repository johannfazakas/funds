package ro.jf.funds.importer.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.service.domain.ImportRecord
import ro.jf.funds.importer.service.domain.ImportTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.util.*

private val log = logger { }

class ImportHandler(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk,
    private val fundTransactionSdk: FundTransactionSdk
) {
    suspend fun import(userId: UUID, importTransactions: List<ImportTransaction>) {
        log.info { "Handling import >> user = $userId items size = ${importTransactions.size}." }

        val transactionRequests = importTransactions
            .toTransactionRequests(createImportResourceContext(userId))
        fundTransactionSdk.createTransactions(userId, CreateFundTransactionsTO(transactionRequests))
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
        accountSdk.listAccounts(userId).associate { it.name to it.id },
        fundSdk.listFunds(userId).associate { it.name to it.id }
    )

    private data class ImportResourceContext(
        private val accountIdByName: Map<AccountName, UUID>,
        private val fundIdByName: Map<FundName, UUID>
    ) {
        fun getAccountId(accountName: AccountName) = accountIdByName[accountName]
            ?: throw ImportDataException("Record account not found: $accountName")

        fun getFundId(fundName: FundName) = fundIdByName[fundName]
            ?: throw ImportDataException("Record fund not found: $fundName")
    }
}
