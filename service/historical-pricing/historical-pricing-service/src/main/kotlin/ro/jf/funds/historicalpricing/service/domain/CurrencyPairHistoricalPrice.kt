package ro.jf.funds.historicalpricing.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import java.math.BigDecimal

data class CurrencyPairHistoricalPrice(
    val sourceCurrency: Currency,
    val targetCurrency: Currency,
    val date: LocalDate,
    val price: BigDecimal
)
