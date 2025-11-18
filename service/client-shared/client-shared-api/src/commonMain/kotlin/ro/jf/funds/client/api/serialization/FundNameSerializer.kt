package ro.jf.funds.client.api.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.client.api.model.FundName

object FundNameSerializer : KSerializer<FundName> {
    override val descriptor = PrimitiveSerialDescriptor("FundName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FundName) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): FundName {
        return FundName(decoder.decodeString())
    }
}
