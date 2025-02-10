package ro.jf.funds.reporting.service.domain

import java.math.BigDecimal

data class ValueReport(
    val startValue: BigDecimal = BigDecimal.ZERO,
    val endValue: BigDecimal = BigDecimal.ZERO,
    val minValue: BigDecimal = BigDecimal.ZERO,
    val maxValue: BigDecimal = BigDecimal.ZERO,
)
