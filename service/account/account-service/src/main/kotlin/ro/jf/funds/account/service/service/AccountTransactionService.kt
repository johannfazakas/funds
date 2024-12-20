package ro.jf.funds.account.service.service

import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.api.model.TransactionsFilterTO
import ro.jf.funds.account.service.domain.Account
import ro.jf.funds.account.service.domain.AccountServiceException
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import java.util.*

class AccountTransactionService(
    private val transactionRepository: AccountTransactionRepository,
    private val accountRepository: AccountRepository,
) {
    suspend fun createTransaction(userId: UUID, request: CreateAccountTransactionTO): AccountTransaction {
        validateTransactionRequests(userId, listOf(request))
        return transactionRepository.save(userId, request)
    }

    suspend fun createTransactions(userId: UUID, requests: CreateAccountTransactionsTO): List<AccountTransaction> {
        validateTransactionRequests(userId, requests.transactions)
        return transactionRepository.saveAll(userId, requests)
    }

    suspend fun listTransactions(
        userId: UUID,
        filter: TransactionsFilterTO
    ): List<AccountTransaction> {
        return transactionRepository.list(userId, filter)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        transactionRepository.deleteById(userId, transactionId)
    }

    private suspend fun validateTransactionRequests(userId: UUID, requests: List<CreateAccountTransactionTO>) {
        val accountsById = getAccounts(userId, requests).associateBy { it.id }
        requests.asSequence()
            .flatMap { it.records }
            .forEach { record ->
                val account = accountsById[record.accountId]
                    ?: throw AccountServiceException.RecordAccountNotFound(record.accountId)
                if (record.unit != account.unit) {
                    throw AccountServiceException.AccountRecordCurrencyMismatch(
                        account.id, account.name, account.unit, record.unit
                    )
                }
            }
    }

    private suspend fun getAccounts(userId: UUID, requests: List<CreateAccountTransactionTO>): List<Account> =
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.accountId }
            .toSet()
            .mapNotNull { accountRepository.findById(userId, it) }
}
