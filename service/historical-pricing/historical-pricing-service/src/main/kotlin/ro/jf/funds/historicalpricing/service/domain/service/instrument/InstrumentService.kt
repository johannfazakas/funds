package ro.jf.funds.historicalpricing.service.domain.service.instrument

import kotlinx.datetime.LocalDate
import mu.KotlinLogging
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.*
import ro.jf.funds.historicalpricing.service.domain.model.InstrumentHistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyService

val log = KotlinLogging.logger {}

class InstrumentService(
    private val instrumentConverterRegistry: ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverterRegistry,
    private val instrumentHistoricalPriceRepository: ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentHistoricalPriceRepository,
    private val currencyService: CurrencyService
) {
    suspend fun convert(request: InstrumentConversionRequest): InstrumentConversionResponse {
        val (instrument, currency, dates) = request

        val storedHistoricalPricesByDate = getStoredHistoricalPricesByDate(instrument, currency, dates)
        val notStoredDates = dates.filterNot { it in storedHistoricalPricesByDate.keys }
        val newConversionsByDate =
            getHistoricalPricesByDate(instrument, currency, notStoredDates)

        return InstrumentConversionResponse(
            instrument = instrument,
            currency = currency,
            historicalPrices = dates.mapNotNull { date ->
                storedHistoricalPricesByDate[date] ?: newConversionsByDate[date]
            }
        )
    }

    private suspend fun getHistoricalPricesByDate(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>
    ): Map<LocalDate, HistoricalPrice> {
        val instrumentConverter = instrumentConverterRegistry.getConverter(instrument)
        val currencyConverter = currencyConverter(instrument, currency, dates)
        return instrumentConverter
            .convert(instrument, dates)
            .mapNotNull(currencyConverter)
            .onEach {
                instrumentHistoricalPriceRepository.saveHistoricalPrice(
                    InstrumentHistoricalPrice(instrument.symbol.value, currency.value, it.date, it.price)
                )
            }
            .associateBy(HistoricalPrice::date)
    }

    private suspend fun getStoredHistoricalPricesByDate(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>
    ) = instrumentHistoricalPriceRepository
        .getHistoricalPrices(instrument.symbol.value, currency.value, dates)
        .map { HistoricalPrice(it.date, it.price) }
        .associateBy { it.date }

    private suspend fun currencyConverter(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>
    ): (HistoricalPrice) -> HistoricalPrice? {
        if (instrument.mainCurrency == currency) {
            return { it }
        }
        val exchangeByDate = currencyService
            .convert(CurrencyConversionRequest(instrument.mainCurrency, currency, dates))
            .historicalPrices.associateBy { it.date }

        return { price ->
            exchangeByDate[price.date]?.let { exchange ->
                HistoricalPrice(price.date, price.price * exchange.price)
            }
        }
    }
}
