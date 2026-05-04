package ro.jf.funds.platform.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.CategorySerializer

@kotlin.jvm.JvmInline
@Serializable(with = CategorySerializer::class)
value class Category(val value: String) {
    init {
        require(value.isNotBlank()) { "Category must not be blank" }
    }

    override fun toString(): String = value
}
