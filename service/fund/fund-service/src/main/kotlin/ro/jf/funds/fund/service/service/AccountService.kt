package ro.jf.funds.fund.service.service

import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.AccountApi
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO
import java.util.*

class AccountService(
    private val accountSdk: AccountSdk,
) : AccountApi {
    override suspend fun listAccounts(userId: UUID): ListTO<AccountTO> = withSuspendingSpan {
        val accountList = accountSdk.listAccounts(userId)
        ListTO(accountList.items.map { it.toFundAccountTO() })
    }

    override suspend fun findAccountById(userId: UUID, accountId: UUID): AccountTO? = withSuspendingSpan {
        accountSdk.findAccountById(userId, accountId)?.toFundAccountTO()
    }

    override suspend fun createAccount(userId: UUID, request: CreateAccountTO): AccountTO = withSuspendingSpan {
        val createRequest = ro.jf.funds.account.api.model.CreateAccountTO(
            name = ro.jf.funds.account.api.model.AccountName(request.name.value),
            unit = request.unit
        )
        accountSdk.createAccount(userId, createRequest).toFundAccountTO()
    }

    override suspend fun deleteAccountById(userId: UUID, accountId: UUID) = withSuspendingSpan {
        accountSdk.deleteAccountById(userId, accountId)
    }

    private fun ro.jf.funds.account.api.model.AccountTO.toFundAccountTO(): AccountTO =
        AccountTO(
            id = this.id,
            name = ro.jf.funds.fund.api.model.AccountName(this.name.value),
            unit = this.unit
        )
}