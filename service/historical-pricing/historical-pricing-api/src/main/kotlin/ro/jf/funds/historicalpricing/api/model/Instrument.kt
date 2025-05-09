package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol

@Serializable
enum class Instrument(val symbol: Symbol, val mainCurrency: Currency, val source: HistoricalPriceSource) {
    // TODO(Johann) how should these instruments be manager, the symbol doesn't seem right, 28304712 is not what would be used in funds
    SXR8_DE(Symbol("SXR8.DE"), Currency.EUR, HistoricalPriceSource.YAHOO),
    QDVE_DE(Symbol("QDVE.DE"), Currency.EUR, HistoricalPriceSource.YAHOO),
    ENUL_DE(Symbol("EUNL.DE"), Currency.EUR, HistoricalPriceSource.YAHOO),
    IMAE_NL(Symbol("IMAEA.XC"), Currency.EUR, HistoricalPriceSource.YAHOO),
    IS3N_DE(Symbol("IS3N.DE"), Currency.EUR, HistoricalPriceSource.YAHOO),
    SUSW_DE(Symbol("SUSW.L"), Currency.EUR, HistoricalPriceSource.YAHOO),
    GOLDMAN_SACHS_RO_EQ(Symbol("28304712"), Currency.RON, HistoricalPriceSource.FINANCIAL_TIMES),
    BT_MAXIM(Symbol("bt-maxim"), Currency.RON, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_INDEX(Symbol("bt-index-romania-rotx"), Currency.RON, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_TECHNOLOGY(Symbol("bt-technology"), Currency.EUR, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_ENERGY(Symbol("bt-energy"), Currency.EUR, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    ;

    companion object {
        fun fromSymbol(symbol: Symbol): Instrument = Instrument.entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("Unknown instrument: $symbol")
    }
}
