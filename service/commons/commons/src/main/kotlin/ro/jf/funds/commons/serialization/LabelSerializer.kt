package ro.jf.funds.commons.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.commons.model.Label

class LabelSerializer : KSerializer<Label> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Label", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Label {
        val value = decoder.decodeString()
        return Label(value)
    }

    override fun serialize(encoder: Encoder, value: Label) {
        encoder.encodeString(value.value)
    }
}