package ro.jf.funds.reporting.service.service.reportdata.forecast

import java.math.BigDecimal

interface ForecastStrategy {
    fun forecastNext(input: List<BigDecimal>): BigDecimal
}
