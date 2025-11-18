package ro.jf.funds.client.core.repository

import com.benasher44.uuid.Uuid
import ro.jf.funds.client.api.model.FundTO
import ro.jf.funds.client.sdk.FundSdk

class FundRepository(
    private val fundSdk: FundSdk = FundSdk()
) {
    suspend fun listFunds(userId: Uuid): List<FundTO> {
        return fundSdk.listFunds(userId).items
    }
}
