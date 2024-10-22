package ro.jf.funds.historicalpricing.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class HistoricalPrice(
    val date: LocalDate,
    @Serializable(with = BigDecimalSerializer::class)
    val price: BigDecimal
)
