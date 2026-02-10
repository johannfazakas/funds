package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.fund.api.model.AccountSortField
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO

interface AccountApi {
    suspend fun listAccounts(
        userId: Uuid,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<AccountSortField>? = null,
    ): PageTO<AccountTO>

    suspend fun findAccountById(userId: Uuid, accountId: Uuid): AccountTO?

    suspend fun createAccount(userId: Uuid, request: CreateAccountTO): AccountTO

    suspend fun deleteAccountById(userId: Uuid, accountId: Uuid)
}