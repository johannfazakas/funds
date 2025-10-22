package ro.jf.funds.historicalpricing.service.service.instrument

import kotlinx.datetime.LocalDate
import mu.KotlinLogging
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.PricingInstrument
import ro.jf.funds.historicalpricing.service.domain.InstrumentHistoricalPrice
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService

val log = KotlinLogging.logger {}

class InstrumentService(
    private val instrumentConverterRegistry: InstrumentConverterRegistry,
    private val instrumentHistoricalPriceRepository: InstrumentHistoricalPriceRepository,
    private val currencyService: CurrencyService,
) {
    suspend fun convert(
        instrument: Instrument,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val storedHistoricalPricesByDate = getStoredHistoricalPricesByDate(instrument, targetCurrency, dates)
        val storedDates = storedHistoricalPricesByDate.map { it.date }.toSet()
        val notStoredDates = dates.filterNot { it in storedDates }
        val newConversionsByDate =
            getHistoricalPricesByDate(instrument, targetCurrency, notStoredDates)

        return storedHistoricalPricesByDate + newConversionsByDate
    }

    private suspend fun getHistoricalPricesByDate(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val pricingInstrument = PricingInstrument.fromInstrument(instrument)
        val instrumentConverter = instrumentConverterRegistry.getConverter(pricingInstrument)

        val currencyConversions = if (pricingInstrument.mainCurrency == currency) {
            emptyMap()
        } else {
            currencyService.convert(pricingInstrument.mainCurrency, currency, dates)
                .associateBy { it.date }
        }

        return instrumentConverter
            .convert(pricingInstrument, dates)
            .map { conversionResponse ->
                val conversionRate = currencyConversions[conversionResponse.date]
                if (conversionRate == null) {
                    conversionResponse
                } else {
                    conversionResponse.copy(
                        rate = conversionResponse.rate * conversionRate.rate
                    )
                }
            }
            .onEach {
                instrumentHistoricalPriceRepository.saveHistoricalPrice(
                    InstrumentHistoricalPrice(pricingInstrument.instrument.value, currency.value, it.date, it.rate)
                )
            }
    }

    private suspend fun getStoredHistoricalPricesByDate(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = instrumentHistoricalPriceRepository
        .getHistoricalPrices(instrument.value, currency.value, dates)
        .map { it: InstrumentHistoricalPrice -> ConversionResponse(instrument, Currency(it.currency), it.date, it.price) }
}
