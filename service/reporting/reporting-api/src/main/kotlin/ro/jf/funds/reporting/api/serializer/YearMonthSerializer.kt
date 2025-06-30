package ro.jf.funds.reporting.api.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ro.jf.funds.reporting.api.model.YearMonthTO

class YearMonthSerializer : KSerializer<YearMonthTO> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("YearMonth", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): YearMonthTO {
        val value = decoder.decodeString()
        val (year, month) = value.split("-").map { it.toInt() }
        return YearMonthTO(year, month)
    }

    override fun serialize(encoder: Encoder, value: YearMonthTO) {
        encoder.encodeString("${value.year}-${value.month}")
    }
}
