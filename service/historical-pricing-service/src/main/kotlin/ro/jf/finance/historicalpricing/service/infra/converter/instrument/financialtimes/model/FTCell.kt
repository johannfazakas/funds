package ro.jf.finance.historicalpricing.service.infra.converter.instrument.financialtimes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class FTCell {
    class Date(val value: LocalDate) : FTCell()
    class Price(val value: BigDecimal) : FTCell()
}
