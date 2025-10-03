package ro.jf.funds.fund.api.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.fund.api.model.AccountName

class AccountNameSerializer : KSerializer<AccountName> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("AccountName", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AccountName {
        val value = decoder.decodeString()
        return AccountName(value)
    }

    override fun serialize(encoder: Encoder, value: AccountName) {
        encoder.encodeString(value.value)
    }
}