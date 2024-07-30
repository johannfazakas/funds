package ro.jf.bk.account.service.domain.port

import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import java.util.*

interface AccountRepository {
    suspend fun list(userId: UUID): List<Account>
    suspend fun findById(userId: UUID, accountId: UUID): Account?
    suspend fun findByName(userId: UUID, name: String): Account?
    suspend fun save(command: CreateCurrencyAccountCommand): Account.Currency
    suspend fun save(command: CreateInstrumentAccountCommand): Account.Instrument
    suspend fun deleteById(userId: UUID, accountId: UUID)
    suspend fun deleteAllByUserId(userId: UUID)
    suspend fun deleteAll()
}
