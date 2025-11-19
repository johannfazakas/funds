package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO

interface AccountApi {
    suspend fun listAccounts(userId: Uuid): ListTO<AccountTO>

    suspend fun findAccountById(userId: Uuid, accountId: Uuid): AccountTO?

    suspend fun createAccount(userId: Uuid, request: CreateAccountTO): AccountTO

    suspend fun deleteAccountById(userId: Uuid, accountId: Uuid)
}