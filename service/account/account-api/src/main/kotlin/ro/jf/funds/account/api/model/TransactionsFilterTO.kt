package ro.jf.funds.account.api.model

const val RECORD_PROPERTIES_PREFIX = "properties.record."
const val TRANSACTION_PROPERTIES_PREFIX = "properties.transaction."

data class TransactionsFilterTO(
    // TODO(Johann) Expenses by fund - add a PropertyTO
    val transactionProperties: Map<String, List<String>>,
    val recordProperties: Map<String, List<String>>,
) {
    companion object {
        fun empty() = TransactionsFilterTO(emptyMap(), emptyMap())
    }
}
