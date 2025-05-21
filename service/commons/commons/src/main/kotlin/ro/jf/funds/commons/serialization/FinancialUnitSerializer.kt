package ro.jf.funds.commons.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.commons.model.UnitType

class FinancialUnitSerializer : KSerializer<FinancialUnit> {

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("FinancialUnit") {
            element<String>("value")
            element<String>("type")
        }

    override fun serialize(encoder: Encoder, value: FinancialUnit) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.value)
            encodeStringElement(descriptor, 1, value.type.value)
        }
    }

    override fun deserialize(decoder: Decoder): FinancialUnit {
        return decoder.decodeStructure(descriptor) {
            var value: String? = null
            var type: UnitType? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> value = decodeStringElement(descriptor, index)
                    1 -> type = decodeStringElement(descriptor, index).let(UnitType::fromString)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index: $index")
                }
            }
            if (value == null || type == null) {
                throw SerializationException("Missing required FinancialUnit fields")
            }

            when (type) {
                UnitType.CURRENCY -> Currency(value)
                UnitType.SYMBOL -> Symbol(value)
            }
        }
    }
}
