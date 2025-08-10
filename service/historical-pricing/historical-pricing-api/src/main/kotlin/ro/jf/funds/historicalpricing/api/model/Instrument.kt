package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol

@Serializable
enum class Instrument(val symbol: Symbol, val conversionSymbol: String, val mainCurrency: Currency, val source: HistoricalPriceSource) {
    // TODO(Johann) how should these instruments be managed, the symbol doesn't seem right, 28304712 is not what would be used in funds
    SXR8_DE(Symbol("SXR8"), "SXR8.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    QDVE_DE(Symbol("QDVE"), "QDVE.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    ENUL_DE(Symbol("EUNL"), "EUNL.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    IMAE_NL(Symbol("IMAE"), "IMAEA.XC", Currency.EUR, HistoricalPriceSource.YAHOO),
    IS3N_DE(Symbol("IS3N"), "IS3N.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    SUSW_DE(Symbol("SUSW"), "SUSW.L", Currency.EUR, HistoricalPriceSource.YAHOO),
    ;

    companion object {
        fun fromSymbol(symbol: Symbol): Instrument = Instrument.entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("Unknown instrument: $symbol")
    }
}
