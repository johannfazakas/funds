package ro.jf.funds.conversion.service.service.instrument.converter.yahoo.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class YahooChartResponse(val chart: Chart) {
    @Serializable
    data class Chart(val result: List<Result>)

    @Serializable
    data class Result(val meta: Meta, val timestamp: List<Long> = emptyList(), val indicators: Indicators)

    @Serializable
    data class Meta(val currency: String, val symbol: String)

    @Serializable
    data class Indicators(val quote: List<Quote> = emptyList())

    @Serializable
    data class Quote(
        val close: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?> = emptyList(),
        val high: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?> = emptyList(),
        val low: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?> = emptyList(),
        val open: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?> = emptyList(),
        val volume: List<Long?> = emptyList()
    )
}
