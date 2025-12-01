package ro.jf.funds.conversion.service.service.instrument.converter.financialtimes.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate

sealed class FTCell {
    class Date(val value: LocalDate) : FTCell()
    class Price(val value: BigDecimal) : FTCell()
}
