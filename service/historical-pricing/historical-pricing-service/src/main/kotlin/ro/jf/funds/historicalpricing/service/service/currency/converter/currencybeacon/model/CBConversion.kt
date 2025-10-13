package ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CBConversion(
    val date: String? = null,
    val base: String? = null,
    @Serializable(with = CBRatesSerializer::class)
    val rates: Map<String, BigDecimal> = emptyMap(),
    val meta: CBMeta? = null,
)

@Serializable
data class CBMeta(
    val code: Int,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_detail") val errorDetail: String? = null,
    val disclaimer: String? = null,
)
