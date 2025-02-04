package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency

@Deprecated("Use ConversionRequest instead")
@Serializable
data class InstrumentConversionRequest(
    val instrument: Instrument,
    val currency: Currency,
    val dates: List<LocalDate>
)
