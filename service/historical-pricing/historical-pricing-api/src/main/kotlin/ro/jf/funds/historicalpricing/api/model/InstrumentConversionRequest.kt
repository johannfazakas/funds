package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate

@Serializable
data class InstrumentConversionRequest(
    val instrument: Instrument,
    val currency: Currency,
    val dates: List<LocalDate>
)
