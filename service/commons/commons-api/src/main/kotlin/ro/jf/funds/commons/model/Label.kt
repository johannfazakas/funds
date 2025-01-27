package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable

private val LABEL_PATTERN = Regex("^[a-zA-Z0-9_]+$")

@JvmInline
@Serializable
value class Label(val value: String) {
    init {
        require(value.isNotBlank()) { "Label must not be blank" }
        require(value.matches(LABEL_PATTERN)) { "Label must contain only letters, numbers or underscore." }
    }

    override fun toString(): String = value
}