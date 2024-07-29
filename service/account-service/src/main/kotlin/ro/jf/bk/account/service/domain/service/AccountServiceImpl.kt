package ro.jf.bk.account.service.domain.service

import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import ro.jf.bk.account.service.domain.port.AccountRepository
import ro.jf.bk.account.service.domain.port.AccountService
import java.util.*

class AccountServiceImpl(
    private val accountRepository: AccountRepository
) : AccountService {
    override suspend fun listAccounts(userId: UUID): List<Account> {
        return accountRepository.list(userId)
    }

    override suspend fun findAccountById(userId: UUID, accountId: UUID): Account? {
        return accountRepository.findById(userId, accountId)
    }

    override suspend fun findAccountByName(userId: UUID, name: String): Account? {
        return accountRepository.findByName(userId, name)
    }

    override suspend fun createAccount(
        userId: UUID,
        createAccountCommand: CreateCurrencyAccountCommand
    ): Account.Currency {
        return accountRepository.save(createAccountCommand)
    }

    override suspend fun createAccount(
        userId: UUID,
        createAccountCommand: CreateInstrumentAccountCommand
    ): Account.Instrument {
        return accountRepository.save(createAccountCommand)
    }

    override suspend fun deleteAccount(userId: UUID, accountId: UUID) {
        accountRepository.deleteById(userId, accountId)
    }
}
