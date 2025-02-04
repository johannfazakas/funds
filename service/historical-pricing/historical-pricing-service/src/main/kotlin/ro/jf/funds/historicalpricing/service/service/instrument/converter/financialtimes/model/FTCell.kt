package ro.jf.funds.historicalpricing.service.service.instrument.converter.financialtimes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class FTCell {
    class Date(val value: LocalDate) : FTCell()
    class Price(val value: BigDecimal) : FTCell()
}
