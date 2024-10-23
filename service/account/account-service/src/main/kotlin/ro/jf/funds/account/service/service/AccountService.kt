package ro.jf.funds.account.service.service

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.account.service.domain.Account
import ro.jf.funds.account.service.persistence.AccountRepository
import java.util.*

class AccountService(
    private val accountRepository: AccountRepository
) {
    suspend fun listAccounts(userId: UUID): List<Account> {
        return accountRepository.list(userId)
    }

    suspend fun findAccountById(userId: UUID, accountId: UUID): Account? {
        return accountRepository.findById(userId, accountId)
    }

    suspend fun findAccountByName(userId: UUID, name: AccountName): Account? {
        return accountRepository.findByName(userId, name)
    }

    suspend fun createAccount(
        userId: UUID,
        createAccountCommand: CreateAccountTO
    ): Account {
        return accountRepository.save(userId, createAccountCommand)
    }

    suspend fun deleteAccount(userId: UUID, accountId: UUID) {
        accountRepository.deleteById(userId, accountId)
    }
}
