package ro.jf.funds.client.web.model

import kotlin.js.JsExport

@JsExport
data class JsUser(
    val id: String,
    val username: String
)
