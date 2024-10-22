package ro.jf.funds.account.service.service

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.CreateCurrencyAccountTO
import ro.jf.funds.account.api.model.CreateInstrumentAccountTO
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
        createAccountCommand: CreateCurrencyAccountTO
    ): Account.Currency {
        return accountRepository.save(userId, createAccountCommand)
    }

    suspend fun createAccount(
        userId: UUID,
        createAccountCommand: CreateInstrumentAccountTO
    ): Account.Instrument {
        return accountRepository.save(userId, createAccountCommand)
    }

    suspend fun deleteAccount(userId: UUID, accountId: UUID) {
        accountRepository.deleteById(userId, accountId)
    }
}
