package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable

@Serializable
data class ListTO<T>(
    val items: List<T>,
) {
    companion object {
        fun <T> of(vararg items: T) = ListTO(items.toList())
    }
}

fun <T> List<T>.toListTO() = ListTO(this)

fun <I, O> List<I>.toListTO(mapper: (I) -> O): ListTO<O> = ListTO(this.map(mapper))
