package ro.jf.funds.account.api

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.commons.model.ListTO
import java.util.*

interface AccountApi {
    suspend fun listAccounts(userId: UUID): ListTO<AccountTO>

    suspend fun findAccountById(userId: UUID, accountId: UUID): AccountTO?

    suspend fun createAccount(userId: UUID, request: CreateAccountTO): AccountTO

    suspend fun deleteAccountById(userId: UUID, accountId: UUID)
}
