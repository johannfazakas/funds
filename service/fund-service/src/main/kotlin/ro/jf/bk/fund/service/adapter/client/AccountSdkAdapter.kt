package ro.jf.bk.fund.service.adapter.client

import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.service.domain.model.Account
import ro.jf.bk.fund.service.domain.port.AccountRepository
import java.util.*

class AccountSdkAdapter(
    private val accountSdk: AccountSdk
) : AccountRepository {
    override suspend fun findById(userId: UUID, accountId: UUID): Account? {
        return accountSdk.findAccountById(userId, accountId)
            ?.let { Account(it.id, it.name) }
    }
}
