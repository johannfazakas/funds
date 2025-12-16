package ro.jf.funds.importer.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit

data class Conversion(
    val date: LocalDate,
    val sourceCurrency: FinancialUnit,
    val targetCurrency: Currency,
)
