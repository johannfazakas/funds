package ro.jf.funds.platform.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.CategorySerializer

private val CATEGORY_PATTERN = Regex("^[a-zA-Z0-9_]+$")

@kotlin.jvm.JvmInline
@Serializable(with = CategorySerializer::class)
value class Category(val value: String) {
    init {
        require(value.isNotBlank()) { "Category must not be blank" }
        require(value.matches(CATEGORY_PATTERN)) { "Category must contain only letters, numbers or underscore." }
    }

    override fun toString(): String = value
}
