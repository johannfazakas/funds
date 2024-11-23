package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.importer.service.domain.Store
import java.util.*

class FundService(
    private val fundSdk: FundSdk,
) {
    suspend fun getFundStore(userId: UUID): Store<FundName, FundTO> = fundSdk
        .listFunds(userId).items
        .associateBy { it.name }
        .let { Store(it) }
}
