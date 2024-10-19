package ro.jf.funds.importer.service.service

import mu.KotlinLogging.logger
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.funds.importer.service.domain.ImportDataException
import ro.jf.funds.importer.service.domain.ImportTransaction
import java.util.*

private val log = logger { }

class ImportHandler(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk
) {
    suspend fun import(userId: UUID, importTransactions: List<ImportTransaction>) {
        log.info { "Handling import >> user = $userId items size = ${importTransactions.size}." }

        val accountIdByName = accountSdk.listAccounts(userId).associate { it.name to it.id }
        val fundIdByName = fundSdk.listFunds(userId).associate { it.name to it.id }

        importTransactions
            .asSequence()
            .flatMap { it.records }
            .forEach { importRecord ->
                if (!accountIdByName.containsKey(importRecord.accountName)) {
                    log.warn { "Account not found: ${importRecord.accountName}" }
                    throw ImportDataException("Record account not found: ${importRecord.accountName}")
                }
                if (!fundIdByName.containsKey(importRecord.fundName)) {
                    log.warn { "Fund not found: ${importRecord.fundName}" }
                    throw ImportDataException("Record fund not found: ${importRecord.fundName}")
                }
            }
    }
}