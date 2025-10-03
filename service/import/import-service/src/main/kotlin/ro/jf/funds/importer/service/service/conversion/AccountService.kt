package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.sdk.FundAccountSdk
import ro.jf.funds.importer.service.domain.Store
import java.util.*

class AccountService(
    private val fundAccountSdk: FundAccountSdk,
) {
    suspend fun getAccountStore(userId: UUID): Store<AccountName, AccountTO> = fundAccountSdk
        .listAccounts(userId).items
        .associateBy { it.name }
        .let { Store(it) }
}
