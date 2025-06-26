package ro.jf.funds.client.notebook.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class InitialBalance(
    val accountName: String,
    val fundName: String,
    val amount: String,
)

@Serializable
data class InitialBalances(
    val date: LocalDate,
    val balances: List<InitialBalance>,
)
