package ro.jf.bk.investment.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class InvestmentPosition(
    val symbol: String,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
)
