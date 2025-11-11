package ro.jf.funds.historicalpricing.service.service.instrument

import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionSource.YAHOO
import ro.jf.funds.historicalpricing.service.domain.ConversionExceptions
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionInfo

class InstrumentConversionInfoRepository {

    private val instrumentsByInstrument = listOf(
        InstrumentConversionInfo(Instrument("SXR8"), YAHOO, "SXR8.DE", EUR),
        InstrumentConversionInfo(Instrument("QDVE"), YAHOO, "QDVE.DE", EUR),
        InstrumentConversionInfo(Instrument("EUNL"), YAHOO, "EUNL.DE", EUR),
        InstrumentConversionInfo(Instrument("IMAE"), YAHOO, "IMAEA.XC", EUR),
        InstrumentConversionInfo(Instrument("IS3N"), YAHOO, "IS3N.DE", EUR),
        InstrumentConversionInfo(Instrument("SUSW"), YAHOO, "SUSW.L", EUR),
    ).associateBy { it.instrument }

    fun findByInstrument(instrument: Instrument): InstrumentConversionInfo =
        instrumentsByInstrument[instrument] ?: throw ConversionExceptions.InstrumentSourceIntegrationNotFound(instrument)
}
