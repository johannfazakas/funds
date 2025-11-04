package ro.jf.funds.historicalpricing.service.service.instrument

import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.service.domain.HistoricalPriceSource.YAHOO
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions
import ro.jf.funds.historicalpricing.service.domain.PricingInstrument

class PricingInstrumentRepository {

    private val instrumentsByInstrument = listOf(
        PricingInstrument(Instrument("SXR8"), YAHOO, "SXR8.DE", EUR),
        PricingInstrument(Instrument("QDVE"), YAHOO, "QDVE.DE", EUR),
        PricingInstrument(Instrument("EUNL"), YAHOO, "EUNL.DE", EUR),
        PricingInstrument(Instrument("IMAE"), YAHOO, "IMAEA.XC", EUR),
        PricingInstrument(Instrument("IS3N"), YAHOO, "IS3N.DE", EUR),
        PricingInstrument(Instrument("SUSW"), YAHOO, "SUSW.L", EUR),
    ).associateBy { it.instrument }

    fun findByInstrument(instrument: Instrument): PricingInstrument =
        instrumentsByInstrument[instrument] ?: throw HistoricalPricingExceptions.InstrumentSourceIntegrationNotFound(instrument)
}
