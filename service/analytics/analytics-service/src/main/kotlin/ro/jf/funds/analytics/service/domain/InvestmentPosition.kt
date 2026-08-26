package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument

data class InvestmentPosition(
    val date: LocalDate,
    val currencyUnit: Currency,
    val currencyAmount: BigDecimal,
    val instrumentUnit: Instrument,
    val instrumentAmount: BigDecimal,
    val fundId: Uuid,
    val accountId: Uuid,
    val category: String?,
)
