package ro.jf.bk.commons.model

import kotlinx.serialization.Serializable

@Serializable
data class ProblemTO(
    val title: String,
    val detail: String? = null
)
