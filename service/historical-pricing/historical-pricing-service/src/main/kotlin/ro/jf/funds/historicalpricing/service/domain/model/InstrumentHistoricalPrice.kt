package ro.jf.funds.historicalpricing.service.domain.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class InstrumentHistoricalPrice(
    val symbol: String,
    val currency: String,
    val date: LocalDate,
    val price: BigDecimal
)
