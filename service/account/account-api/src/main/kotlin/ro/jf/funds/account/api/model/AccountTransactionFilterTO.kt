package ro.jf.funds.account.api.model

import kotlinx.datetime.LocalDate

const val RECORD_PROPERTIES_PREFIX = "properties.record."
const val TRANSACTION_PROPERTIES_PREFIX = "properties.transaction."

data class AccountTransactionFilterTO(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val transactionProperties: List<PropertyTO> = propertiesOf(),
    val recordProperties: List<PropertyTO> = propertiesOf(),
) {
    companion object {
        fun empty() = AccountTransactionFilterTO(null, null, emptyList(), emptyList())
    }
}
