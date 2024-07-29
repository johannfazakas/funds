package ro.jf.bk.fund.api

import ro.jf.bk.fund.api.model.FundTO
import java.util.*

interface FundApi {
    suspend fun listFunds(userId: UUID): List<FundTO>
}
