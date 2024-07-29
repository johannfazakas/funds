package ro.jf.finance.historicalpricing.service.domain.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class CurrencyPairHistoricalPrice(
    val sourceCurrency: String,
    val targetCurrency: String,
    val date: LocalDate,
    val price: BigDecimal
)
