package ro.jf.funds.account.api.model

import kotlinx.serialization.Serializable

fun propertiesOf(vararg properties: Pair<String, String>): List<PropertyTO> =
    properties.map { (key, value) -> PropertyTO(key, value) }

@Serializable
data class PropertyTO(
    val key: String,
    val value: String,
) {
    constructor(pair: Pair<String, String>) : this(pair.first, pair.second)
}
