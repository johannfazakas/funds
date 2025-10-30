package ro.jf.funds.reporting.service.service.reportdata.forecast

import java.math.BigDecimal
import java.math.MathContext

class AverageForecastStrategy : ForecastStrategy {
    override fun forecastNext(input: List<BigDecimal>): BigDecimal {
        if (input.isEmpty()) return BigDecimal.ZERO
        return input.sumOf { it }.divide(input.size.toBigDecimal(), MathContext.DECIMAL32)
    }
}
