package ro.jf.funds.historicalpricing.service.service.instrument

import kotlinx.datetime.LocalDate
import mu.KotlinLogging
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.Instrument
import ro.jf.funds.historicalpricing.service.domain.InstrumentHistoricalPrice
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService

val log = KotlinLogging.logger {}

class InstrumentService(
    private val instrumentConverterRegistry: InstrumentConverterRegistry,
    private val instrumentHistoricalPriceRepository: InstrumentHistoricalPriceRepository,
    private val currencyService: CurrencyService,
) {
    suspend fun convert(
        symbol: Symbol,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val storedHistoricalPricesByDate = getStoredHistoricalPricesByDate(symbol, targetCurrency, dates)
        val storedDates = storedHistoricalPricesByDate.map { it.date }.toSet()
        val notStoredDates = dates.filterNot { it in storedDates }
        val newConversionsByDate =
            getHistoricalPricesByDate(symbol, targetCurrency, notStoredDates)

        return storedHistoricalPricesByDate + newConversionsByDate
    }

    private suspend fun getHistoricalPricesByDate(
        symbol: Symbol,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val instrument = Instrument.fromSymbol(symbol)
        val instrumentConverter = instrumentConverterRegistry.getConverter(instrument)

        val currencyConversions = if (instrument.mainCurrency == currency) {
            emptyMap()
        } else {
            currencyService.convert(instrument.mainCurrency, currency, dates)
                .associateBy { it.date }
        }

        return instrumentConverter
            .convert(instrument, dates)
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
                    InstrumentHistoricalPrice(instrument.symbol.value, currency.value, it.date, it.rate)
                )
            }
    }

    private suspend fun getStoredHistoricalPricesByDate(
        symbol: Symbol,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = instrumentHistoricalPriceRepository
        .getHistoricalPrices(symbol.value, currency.value, dates)
        .map { it: InstrumentHistoricalPrice -> ConversionResponse(symbol, Currency(it.currency), it.date, it.price) }
}
