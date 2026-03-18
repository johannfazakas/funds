package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionsCreatedTO(
    val transactions: List<TransactionTO>,
)
