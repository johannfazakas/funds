package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDate

data class FundTransactionFilterTO(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    companion object {
        fun empty() = FundTransactionFilterTO()
    }
}
