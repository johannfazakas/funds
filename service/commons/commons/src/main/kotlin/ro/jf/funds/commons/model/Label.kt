package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable

private val LABEL_PATTERN = Regex("^[a-zA-Z0-9_]+$")
private const val LABEL_SEPARATOR = ","

fun List<Label>.asString(): String = joinToString(",") { it.value }
fun String.asLabels(): List<Label> = split(LABEL_SEPARATOR).filter { it.isNotBlank() }.map { Label(it) }
fun labelsOf(vararg labels: String): List<Label> = labels.map(::Label)

@JvmInline
@Serializable
value class Label(val value: String) {
    init {
        require(value.isNotBlank()) { "Label must not be blank" }
        require(value.matches(LABEL_PATTERN)) { "Label must contain only letters, numbers or underscore." }
    }

    override fun toString(): String = value
}
