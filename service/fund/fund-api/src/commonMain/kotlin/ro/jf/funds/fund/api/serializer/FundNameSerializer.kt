package ro.jf.funds.fund.api.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.fund.api.model.FundName

class FundNameSerializer : KSerializer<FundName> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("FundName", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FundName {
        val value = decoder.decodeString()
        return FundName(value)
    }

    override fun serialize(encoder: Encoder, value: FundName) {
        encoder.encodeString(value.value)
    }
}