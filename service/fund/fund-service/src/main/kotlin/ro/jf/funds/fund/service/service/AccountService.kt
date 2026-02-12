package ro.jf.funds.fund.service.service

import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountSortField
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.api.model.UpdateAccountTO
import ro.jf.funds.fund.service.domain.Account
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import java.util.*

class AccountService(
    private val accountRepository: AccountRepository,
    private val recordRepository: RecordRepository,
) {
    suspend fun listAccounts(
        userId: UUID,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<AccountSortField>?,
    ): PagedResult<Account> {
        return accountRepository.list(userId, pageRequest, sortRequest)
    }

    suspend fun findAccountById(userId: UUID, accountId: UUID): Account {
        return accountRepository.findById(userId, accountId)
            ?: throw FundServiceException.AccountNotFound(accountId)
    }

    suspend fun createAccount(
        userId: UUID,
        createAccountCommand: CreateAccountTO
    ): Account {
        val existingAccount = findAccountByName(userId, createAccountCommand.name)
        if (existingAccount != null) {
            throw FundServiceException.AccountNameAlreadyExists(createAccountCommand.name)
        }
        return accountRepository.save(userId, createAccountCommand)
    }

    suspend fun deleteAccount(userId: UUID, accountId: UUID) {
        val records = recordRepository.list(userId, RecordFilter(accountId = accountId))
        if (records.items.isNotEmpty()) {
            throw FundServiceException.AccountHasRecords(accountId)
        }
        accountRepository.deleteById(userId, accountId)
    }

    suspend fun updateAccount(userId: UUID, accountId: UUID, request: UpdateAccountTO): Account {
        val existingAccount = accountRepository.findById(userId, accountId)
            ?: throw FundServiceException.AccountNotFound(accountId)

        val newName = request.name
        if (newName != null && newName != existingAccount.name) {
            val existingWithName = accountRepository.findByName(userId, newName)
            if (existingWithName != null) {
                throw FundServiceException.AccountNameAlreadyExists(newName)
            }
        }

        val newUnit = request.unit
        if (newUnit != null && newUnit != existingAccount.unit) {
            val records = recordRepository.list(userId, RecordFilter(accountId = accountId))
            if (records.items.isNotEmpty()) {
                throw FundServiceException.AccountHasRecords(accountId)
            }
        }

        return accountRepository.update(userId, accountId, request)
            ?: throw FundServiceException.AccountNotFound(accountId)
    }

    private suspend fun findAccountByName(userId: UUID, name: AccountName): Account? {
        return accountRepository.findByName(userId, name)
    }
}