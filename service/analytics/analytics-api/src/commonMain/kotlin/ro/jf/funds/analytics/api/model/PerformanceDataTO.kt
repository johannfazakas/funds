@file:UseSerializers(BigDecimalSerializer::class)

package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class PerformanceDataTO(
    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,
    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,
    val totalInstrumentValue: BigDecimal,
    val currencyValue: BigDecimal,
)
