package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ReportViewTO(
    val name: String,
    val fundId: String,
    val type: ReportViewTypeTO,
)
