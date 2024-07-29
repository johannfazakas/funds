package ro.jf.bk.investment.api.model

import kotlinx.serialization.Serializable

// Johann! could extract in some commons-api library
@Serializable
data class ListTO<T>(
    val items: List<T>
)
