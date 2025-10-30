package ro.jf.funds.reporting.service.service.reportdata.forecast

import org.nield.kotlinstatistics.simpleRegression
import java.math.BigDecimal

class LinearRegressionForecastStrategy : ForecastStrategy {
    override fun forecastNext(input: List<BigDecimal>): BigDecimal {
        val data = input.mapIndexed { index, value -> index.toDouble() to value }
        val model = data.simpleRegression()
        return model.predict(input.size.toDouble()).toBigDecimal()
    }
}
