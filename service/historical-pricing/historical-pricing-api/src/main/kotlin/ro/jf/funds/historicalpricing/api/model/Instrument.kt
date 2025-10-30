package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument

@Serializable
enum class PricingInstrument(val instrument: Instrument, val conversionSymbol: String, val mainCurrency: Currency, val source: HistoricalPriceSource) {
    SXR8_DE(Instrument("SXR8"), "SXR8.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    QDVE_DE(Instrument("QDVE"), "QDVE.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    EUNL_DE(Instrument("EUNL"), "EUNL.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    IMAE_NL(Instrument("IMAE"), "IMAEA.XC", Currency.EUR, HistoricalPriceSource.YAHOO),
    IS3N_DE(Instrument("IS3N"), "IS3N.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    SUSW_DE(Instrument("SUSW"), "SUSW.L", Currency.EUR, HistoricalPriceSource.YAHOO),
    ;

    companion object {
        fun fromInstrument(instrument: Instrument): PricingInstrument = PricingInstrument.entries.firstOrNull { it.instrument == instrument }
            ?: throw IllegalArgumentException("Unknown instrument: $instrument")
    }
}
