package ro.jf.funds.conversion.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.api.model.Currency
import ro.jf.funds.commons.api.model.FinancialUnit

data class Conversion(
    val source: FinancialUnit,
    val target: Currency,
    val date: LocalDate,
    val price: BigDecimal
)
