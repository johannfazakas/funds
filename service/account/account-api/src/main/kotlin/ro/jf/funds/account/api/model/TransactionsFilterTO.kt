package ro.jf.funds.account.api.model

const val RECORD_PROPERTIES_PREFIX = "properties.record."
const val TRANSACTION_PROPERTIES_PREFIX = "properties.transaction."

data class TransactionsFilterTO(
    val transactionProperties: List<PropertyTO>,
    val recordProperties: List<PropertyTO>,
) {
    companion object {
        fun empty() = TransactionsFilterTO(emptyList(), emptyList())
    }
}
