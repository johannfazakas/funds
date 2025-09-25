package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// TODO(Johann-performance-interest) review usage
data class InvestmentOpenPosition(
    val date: LocalDate,
    val amount: BigDecimal,
)