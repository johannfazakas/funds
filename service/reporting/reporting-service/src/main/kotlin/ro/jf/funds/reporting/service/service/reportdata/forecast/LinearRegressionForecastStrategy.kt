package ro.jf.funds.reporting.service.service.reportdata.forecast

import mu.KotlinLogging.logger
import org.nield.kotlinstatistics.simpleRegression
import java.math.BigDecimal

private val log = logger { }

class LinearRegressionForecastStrategy : ForecastStrategy {
    override fun forecastNext(input: List<BigDecimal>): BigDecimal {
        val data = input.mapIndexed { index, value -> index.toDouble() to value }
        return when (data.size) {
            0 -> return BigDecimal.ZERO
            1 -> data[0].second
            else -> {
                val model = data.simpleRegression()
                model.predict(input.size.toDouble()).toBigDecimal()
            }
        }
    }
}
