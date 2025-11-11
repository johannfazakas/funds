package ro.jf.funds.historicalpricing.service.service

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.service.domain.HistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.PricingInstrument
import ro.jf.funds.historicalpricing.service.persistence.HistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.historicalpricing.service.service.instrument.PricingInstrumentRepository

class ConversionService(
    private val historicalPriceRepository: HistoricalPriceRepository,
    private val currencyConverter: CurrencyConverter,
    private val pricingInstrumentRepository: PricingInstrumentRepository,
    private val instrumentConverterRegistry: InstrumentConverterRegistry,
) {
    suspend fun convert(request: ConversionsRequest): ConversionsResponse {
        return request.conversions
            .groupBy { it.sourceUnit to it.targetCurrency }
            .map { (currencyPair, requests) ->
                convert(currencyPair.first, currencyPair.second, requests.map { it.date })
            }
            .flatten()
            .let { ConversionsResponse(it) }
    }

    private suspend fun convert(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> =
        when (sourceUnit) {
            is Currency -> convert(sourceUnit, targetCurrency, dates, ::getCurrencyConversions)
            is Instrument -> convert(sourceUnit, targetCurrency, dates, ::getInstrumentConversions)
        }

    private suspend fun <T : FinancialUnit> convert(
        sourceUnit: T,
        targetCurrency: Currency,
        dates: List<LocalDate>,
        conversionFunction: suspend (T, Currency, List<LocalDate>) -> List<ConversionResponse>,
    ): List<ConversionResponse> {
        val storedHistoricalPricesByDate = getStoredHistoricalPrices(sourceUnit, targetCurrency, dates)
        val notStoredDates = getNotStoredDates(storedHistoricalPricesByDate, dates)

        val newConversions = if (notStoredDates.isNotEmpty()) {
            conversionFunction.invoke(sourceUnit, targetCurrency, notStoredDates)
                .also { it.storeHistoricalPrices() }
        } else {
            emptyList()
        }

        return storedHistoricalPricesByDate + newConversions
    }

    private suspend fun getCurrencyConversions(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = currencyConverter.convert(sourceCurrency, targetCurrency, dates)

    private suspend fun getInstrumentConversions(
        instrument: Instrument,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val pricingInstrument = pricingInstrumentRepository.findByInstrument(instrument)

        val conversions = if (pricingInstrument.mainCurrency == currency) {
            getInstrumentMainCurrencyConversions(pricingInstrument, dates)
        } else {
            getInstrumentConversionsWithImplicitConversion(pricingInstrument, currency, dates)
        }
        return conversions
    }

    private suspend fun getInstrumentMainCurrencyConversions(
        pricingInstrument: PricingInstrument,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val instrumentConverter = instrumentConverterRegistry.getConverter(pricingInstrument)
        return instrumentConverter.convert(pricingInstrument, dates)
    }

    private suspend fun getInstrumentConversionsWithImplicitConversion(
        pricingInstrument: PricingInstrument,
        currency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val instrumentConverter = instrumentConverterRegistry.getConverter(pricingInstrument)
        val currencyConversions =
            convert(pricingInstrument.mainCurrency, currency, dates, ::getCurrencyConversions)
                .associateBy { it.date }

        return instrumentConverter
            .convert(pricingInstrument, dates)
            .map { conversionResponse ->
                val conversionRate = currencyConversions[conversionResponse.date]
                    ?: error("Implicit currency conversion rate for $conversionResponse to $currency not found")
                conversionResponse.copy(
                    targetCurrency = currency,
                    rate = conversionResponse.rate * conversionRate.rate
                )
            }
    }

    private suspend fun getStoredHistoricalPrices(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = historicalPriceRepository
        .getHistoricalPrices(sourceUnit, targetCurrency, dates)
        .map { ConversionResponse(sourceUnit, it.target, it.date, it.price) }

    private suspend fun List<ConversionResponse>.storeHistoricalPrices() {
        this.forEach {
            historicalPriceRepository.saveHistoricalPrice(
                HistoricalPrice(it.sourceUnit, it.targetCurrency, it.date, it.rate)
            )
        }
    }

    private fun getNotStoredDates(
        storedHistoricalPrices: List<ConversionResponse>,
        dates: List<LocalDate>,
    ): List<LocalDate> {
        val storedDates = storedHistoricalPrices.map { it.date }.toSet()
        return dates.filterNot { it in storedDates }
    }
}
