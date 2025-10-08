package ro.jf.funds.fund.service.service

import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.service.domain.Account
import ro.jf.funds.fund.service.domain.Fund
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.persistence.TransactionRepository
import java.util.*

class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val fundService: FundService,
    private val accountService: AccountService,
) {
    suspend fun listTransactions(
        userId: UUID,
        filter: TransactionFilterTO = TransactionFilterTO.empty(),
    ): List<Transaction> = withSuspendingSpan {
        transactionRepository.list(userId, filter)
    }

    suspend fun createTransaction(userId: UUID, request: CreateTransactionTO): Transaction = withSuspendingSpan {
        validateTransactionRequests(userId, listOf(request))
        transactionRepository.save(userId, request)
    }

    suspend fun createTransactions(userId: UUID, request: CreateTransactionsTO): List<Transaction> =
        withSuspendingSpan {
            validateTransactionRequests(userId, request.transactions)
            transactionRepository.saveAll(userId, request)
        }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) = withSuspendingSpan {
        transactionRepository.deleteById(userId, transactionId)
    }

    private suspend fun validateTransactionRequests(userId: UUID, requests: List<CreateTransactionTO>) {
        val accountsById = getAccounts(userId, requests).associateBy { it.id }
        val fundsById = getFunds(userId, requests).associateBy { it.id }

        requests.asSequence()
            .flatMap { it.records }
            .forEach { record ->
                val account = accountsById[record.accountId]
                    ?: throw FundServiceException.RecordAccountNotFound(record.accountId)
                if (record.unit != account.unit) {
                    throw FundServiceException.AccountRecordCurrencyMismatch(
                        account.id, account.name, account.unit, record.unit
                    )
                }
                fundsById[record.fundId] ?: throw FundServiceException.TransactionFundNotFound(record.fundId)
            }
    }

    // TODO(Johann) get accounts and get funds might be optimized to avoid N calls
    private suspend fun getAccounts(userId: UUID, requests: List<CreateTransactionTO>): List<Account> =
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.accountId }
            .toSet()
            .map { accountService.findAccountById(userId, it) }

    private suspend fun getFunds(userId: UUID, requests: List<CreateTransactionTO>): List<Fund> =
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.fundId }
            .toSet()
            .mapNotNull { fundService.findById(userId, it) }
}
