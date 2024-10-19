package ro.jf.bk.fund.service.service

import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.service.domain.Account
import java.util.*

// TODO(Johann) arguably not needed
class AccountSdkAdapter(
    private val accountSdk: AccountSdk
) {
    suspend fun findById(userId: UUID, accountId: UUID): Account? {
        return accountSdk.findAccountById(userId, accountId)
            ?.let { Account(it.id, it.name.value) }
    }
}
