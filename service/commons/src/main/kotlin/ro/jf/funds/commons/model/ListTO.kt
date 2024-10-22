package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable

@Serializable
data class ListTO<T>(
    val items: List<T>
)

fun <T> List<T>.toListTO() = ro.jf.funds.commons.model.ListTO(this)

fun <I, O> List<I>.toListTO(mapper: (I) -> O): ro.jf.funds.commons.model.ListTO<O> =
    ro.jf.funds.commons.model.ListTO(this.map(mapper))
