package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.importer.service.domain.Store
import java.util.*

class AccountService(
    private val accountSdk: AccountSdk,
) {
    suspend fun getAccountStore(userId: UUID): Store<AccountName, AccountTO> = accountSdk
        .listAccounts(userId).items
        .associateBy { it.name }
        .let { Store(it) }
}
