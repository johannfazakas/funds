package ro.jf.funds.importer.service.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.api.model.CreateFundRecordTO
import ro.jf.bk.fund.api.model.CreateFundTransactionTO
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.bk.fund.sdk.FundTransactionSdk
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
    suspend fun import(userId: UUID, importTransactions: List<ImportTransaction>) = coroutineScope {
        log.info { "Handling import >> user = $userId items size = ${importTransactions.size}." }

        importTransactions
            .toTransactionRequests(createImportResourceContext(userId))
            .map { request -> async { fundTransactionSdk.createTransaction(userId, request) } }
            .forEach { it.await() }
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
            amount = amount
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
