package ro.jf.funds.conversion.service.service.currency.converter.currencybeacon.model

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import ro.jf.funds.platform.jvm.serialization.BigDecimalSerializer
import java.math.BigDecimal

object CBRatesSerializer : KSerializer<Map<String, BigDecimal?>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PolymorphicRates")

    override fun serialize(encoder: Encoder, value: Map<String, BigDecimal?>) {
        val mapSerializer = MapSerializer(String.serializer(), BigDecimalSerializer().nullable)
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, BigDecimal?> {
        val jsonDecoder = decoder as JsonDecoder

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> emptyMap() // Empty array case
            is JsonObject -> {
                // Object case - deserialize as normal map
                val mapSerializer = MapSerializer(String.serializer(), BigDecimalSerializer().nullable)
                decoder.json.decodeFromJsonElement(mapSerializer, element)
            }
            else -> emptyMap()
        }
    }
}