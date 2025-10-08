package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.service.domain.Account
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.persistence.AccountRepository
import java.util.*

class AccountService(
    private val accountRepository: AccountRepository
) {
    suspend fun listAccounts(userId: UUID): List<Account> {
        return accountRepository.list(userId)
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
        accountRepository.deleteById(userId, accountId)
    }

    private suspend fun findAccountByName(userId: UUID, name: AccountName): Account? {
        return accountRepository.findByName(userId, name)
    }
}