package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundSortField
import ro.jf.funds.fund.api.model.FundTO

interface FundApi {
    suspend fun getFundById(userId: Uuid, fundId: Uuid): FundTO?
    suspend fun getFundByName(userId: Uuid, name: FundName): FundTO?
    suspend fun listFunds(
        userId: Uuid,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<FundSortField>? = null,
    ): PageTO<FundTO>
    suspend fun createFund(userId: Uuid, request: CreateFundTO): FundTO
}
