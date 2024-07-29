package ro.jf.finance.historicalpricing.service.infra.converter.currency.currencybeacon.model

import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class CBConversion(
    val date: String,
    val base: String,
    val rates: Map<String, @Serializable(with = BigDecimalSerializer::class) BigDecimal>
)
