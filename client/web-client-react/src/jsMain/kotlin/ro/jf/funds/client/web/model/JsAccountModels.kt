package ro.jf.funds.client.web.model

import kotlin.js.JsExport

@JsExport
data class JsAccount(
    val id: String,
    val name: String,
    val unitType: String,
    val unitValue: String,
)
