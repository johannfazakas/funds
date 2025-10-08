package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDate
import java.util.*

data class TransactionFilterTO(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val fundId: UUID? = null,
) {
    companion object {
        fun empty() = TransactionFilterTO(null, null, null)
    }
}
