package ro.jf.bk.fund.sdk

import ro.jf.bk.fund.api.FundApi
import ro.jf.bk.fund.api.model.FundTO
import java.util.*

class FundSdk : FundApi {
    override suspend fun listFunds(userId: UUID): List<FundTO> {
        return emptyList()
    }
}