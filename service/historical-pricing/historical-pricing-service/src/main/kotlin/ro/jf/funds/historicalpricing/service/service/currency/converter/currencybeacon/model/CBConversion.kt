package ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class CBConversion(
    val date: String,
    val base: String,
    val rates: Map<String, @Serializable(with = BigDecimalSerializer::class) BigDecimal>,
)
