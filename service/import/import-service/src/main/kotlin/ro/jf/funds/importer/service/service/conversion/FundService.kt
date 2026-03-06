package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.service.domain.Store

class FundService(
    private val fundSdk: FundSdk,
) {
    suspend fun getFundStore(userId: Uuid): Store<FundName, FundTO> = fundSdk
        .listFunds(userId).items
        .associateBy { it.name }
        .let { Store(it) }
}
