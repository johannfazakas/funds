package ro.jf.funds.fund.service.service

import mu.KotlinLogging.logger
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

private val log = logger { }

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
        validateRecordFunds(userId, requests).associateBy { it.id }
        val accountsById = validateRecordAccounts(userId, requests).associateBy { it.id }

        requests.asSequence()
            .flatMap { it.records }
            .mapNotNull { record -> accountsById[record.accountId]?.let { record to it } }
            .forEach { (record, account) ->
                if (record.unit != account.unit) {
                    throw FundServiceException.AccountRecordCurrencyMismatch(
                        account.id, account.name, account.unit, record.unit
                    )
                }
            }
    }

    private suspend fun validateRecordAccounts(userId: UUID, requests: List<CreateTransactionTO>): List<Account> =
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.accountId }
            .toSet()
            .map { accountId ->
                try {
                    accountService.findAccountById(userId, accountId)
                } catch (e: FundServiceException.AccountNotFound) {
                    log.warn(e) { "Account with id $accountId required on transaction record could not be found." }
                    throw FundServiceException.RecordAccountNotFound(accountId)
                }
            }

    private suspend fun validateRecordFunds(userId: UUID, requests: List<CreateTransactionTO>): List<Fund> =
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.fundId }
            .toSet()
            .mapNotNull { fundId ->
                try {
                    fundService.findById(userId, fundId)
                } catch (e: FundServiceException.FundNotFound) {
                    log.warn(e) { "Fund with id $fundId required on transaction record could not be found." }
                    throw FundServiceException.RecordFundNotFound(fundId)
                }
            }
}
