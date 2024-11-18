package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable

// TODO(Johann) this should be removed, there's already one in commons
@Serializable
enum class Instrument(val symbol: String, val mainCurrency: Currency, val source: HistoricalPriceSource) {
    SXR8_DE("SXR8.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    QDVE_DE("QDVE.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    ENUL_DE("EUNL.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    IMAE_NL("IMAEA.XC", Currency.EUR, HistoricalPriceSource.YAHOO),
    IS3N_DE("IS3N.DE", Currency.EUR, HistoricalPriceSource.YAHOO),
    SUSW_DE("SUSW.L", Currency.EUR, HistoricalPriceSource.YAHOO),
    GOLDMAN_SACHS_RO_EQ("28304712", Currency.RON, HistoricalPriceSource.FINANCIAL_TIMES),
    BT_MAXIM("bt-maxim", Currency.RON, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_INDEX("bt-index-romania-rotx", Currency.RON, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_TECHNOLOGY("bt-technology", Currency.EUR, HistoricalPriceSource.BT_ASSET_MANAGEMENT),
    BT_ENERGY("bt-energy", Currency.EUR, HistoricalPriceSource.BT_ASSET_MANAGEMENT);
}
