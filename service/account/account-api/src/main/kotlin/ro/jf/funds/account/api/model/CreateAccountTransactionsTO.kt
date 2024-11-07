package ro.jf.funds.account.api.model

import kotlinx.serialization.Serializable


@Serializable
data class CreateAccountTransactionsTO(
    val transactions: List<CreateAccountTransactionTO>,
)
