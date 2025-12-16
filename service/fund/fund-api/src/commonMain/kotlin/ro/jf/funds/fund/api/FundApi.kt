package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO

interface FundApi {
    suspend fun getFundById(userId: Uuid, fundId: Uuid): FundTO?
    suspend fun getFundByName(userId: Uuid, name: FundName): FundTO?
    suspend fun listFunds(userId: Uuid): ListTO<FundTO>
    suspend fun createFund(userId: Uuid, request: CreateFundTO): FundTO
}
