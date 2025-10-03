package ro.jf.funds.fund.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO
import java.util.*

interface AccountApi {
    suspend fun listAccounts(userId: UUID): ListTO<AccountTO>

    suspend fun findAccountById(userId: UUID, accountId: UUID): AccountTO?

    suspend fun createAccount(userId: UUID, request: CreateAccountTO): AccountTO

    suspend fun deleteAccountById(userId: UUID, accountId: UUID)
}