package ro.jf.bk.account.service.domain.port

import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import java.util.*

interface AccountService {
    suspend fun listAccounts(userId: UUID): List<Account>
    suspend fun findAccountById(userId: UUID, accountId: UUID): Account?
    suspend fun findAccountByName(userId: UUID, name: String): Account?
    suspend fun createAccount(userId: UUID, createAccountCommand: CreateCurrencyAccountCommand): Account
    suspend fun createAccount(userId: UUID, createAccountCommand: CreateInstrumentAccountCommand): Account
    suspend fun deleteAccount(userId: UUID, accountId: UUID)
}
