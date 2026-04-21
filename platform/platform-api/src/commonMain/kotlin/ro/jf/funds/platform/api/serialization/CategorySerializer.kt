package ro.jf.funds.platform.api.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.platform.api.model.Category

class CategorySerializer : KSerializer<Category> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Category", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Category {
        val value = decoder.decodeString()
        return Category(value)
    }

    override fun serialize(encoder: Encoder, value: Category) {
        encoder.encodeString(value.value)
    }
}
