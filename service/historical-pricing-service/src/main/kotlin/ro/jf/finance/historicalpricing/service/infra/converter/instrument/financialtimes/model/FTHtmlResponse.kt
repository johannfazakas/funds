package ro.jf.finance.historicalpricing.service.infra.converter.instrument.financialtimes.model

import kotlinx.serialization.Serializable

@Serializable
data class FTHtmlResponse(
    val html: String
)
