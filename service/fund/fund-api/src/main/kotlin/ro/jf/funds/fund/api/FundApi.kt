package ro.jf.funds.fund.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundTO
import java.util.*

interface FundApi {
    suspend fun listFunds(userId: UUID): ListTO<FundTO>
    suspend fun createFund(userId: UUID, request: CreateFundTO): FundTO
}
