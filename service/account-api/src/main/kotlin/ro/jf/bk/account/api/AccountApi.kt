package ro.jf.bk.account.api

import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import java.util.*

interface AccountApi {
    suspend fun listAccounts(userId: UUID): List<AccountTO>

    suspend fun findAccountById(userId: UUID, accountId: UUID): AccountTO?

    suspend fun createAccount(userId: UUID, request: CreateCurrencyAccountTO): AccountTO.Currency

    suspend fun createAccount(userId: UUID, request: CreateInstrumentAccountTO): AccountTO.Instrument

    suspend fun deleteAccountById(userId: UUID, accountId: UUID)
}
