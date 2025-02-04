package ro.jf.funds.historicalpricing.service.service.instrument.converter.financialtimes.model

import kotlinx.serialization.Serializable

@Serializable
data class FTHtmlResponse(
    val html: String
)
