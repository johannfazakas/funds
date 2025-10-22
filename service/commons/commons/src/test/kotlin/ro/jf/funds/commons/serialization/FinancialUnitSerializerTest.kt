package ro.jf.funds.commons.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.commons.model.UnitType

class FinancialUnitSerializerTest {

    @Test
    fun `should serialize currency`() {
        val currency = Currency.EUR

        val jsonEncoding = Json.encodeToJsonElement(FinancialUnitSerializer(), currency)

        assertThat(jsonEncoding).isEqualTo(buildJsonObject {
            put("value", JsonPrimitive(currency.value))
            put("type", JsonPrimitive(UnitType.CURRENCY.value))
        })
    }


    @Test
    fun `should serialize instrument`() {
        val instrument = Instrument("SXR8_DE")

        val jsonEncoding = Json.encodeToJsonElement(FinancialUnitSerializer(), instrument)

        assertThat(jsonEncoding).isEqualTo(buildJsonObject {
            put("value", JsonPrimitive(instrument.value))
            put("type", JsonPrimitive(UnitType.INSTRUMENT.value))
        })
    }

    @Test
    fun `should deserialize currency`() {
        val json = buildJsonObject {
            put("value", JsonPrimitive(Currency.EUR.value))
            put("type", JsonPrimitive(UnitType.CURRENCY.value))
        }.toString()

        Json.decodeFromString<FinancialUnit>(json)
    }

    @Test
    fun `should deserialize instrument`() {
        val json = buildJsonObject {
            put("value", JsonPrimitive(Instrument("SXR8_DE").value))
            put("type", JsonPrimitive(UnitType.INSTRUMENT.value))
        }.toString()

        Json.decodeFromString<FinancialUnit>(json)
    }
}
