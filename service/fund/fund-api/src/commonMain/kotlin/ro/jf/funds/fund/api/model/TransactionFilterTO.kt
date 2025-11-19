package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import kotlinx.datetime.LocalDate

data class TransactionFilterTO(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val fundId: Uuid? = null,
) {
    companion object {
        fun empty() = TransactionFilterTO(null, null, null)
    }
}
