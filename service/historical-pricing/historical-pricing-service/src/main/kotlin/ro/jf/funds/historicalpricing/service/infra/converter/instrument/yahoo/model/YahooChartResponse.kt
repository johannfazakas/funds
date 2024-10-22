package ro.jf.funds.historicalpricing.service.infra.converter.instrument.yahoo.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class YahooChartResponse(val chart: Chart) {
    @Serializable
    data class Chart(val result: List<Result>)

    @Serializable
    data class Result(val meta: Meta, val timestamp: List<Long>, val indicators: Indicators)

    @Serializable
    data class Meta(val currency: String, val symbol: String)

    @Serializable
    data class Indicators(val quote: List<Quote>)

    @Serializable
    data class Quote(
        val close: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
        val high: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
        val low: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
        val open: List<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
        val volume: List<Long?>
    )
}
