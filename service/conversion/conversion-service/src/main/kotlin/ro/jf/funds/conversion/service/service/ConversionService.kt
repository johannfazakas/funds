package ro.jf.funds.conversion.service.service

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.service.domain.Conversion
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.persistence.ConversionRepository
import ro.jf.funds.conversion.service.service.currency.CurrencyConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.conversion.service.service.instrument.InstrumentConversionInfoRepository

class ConversionService(
    private val conversionRepository: ConversionRepository,
    private val currencyConverter: CurrencyConverter,
    private val instrumentConversionInfoRepository: InstrumentConversionInfoRepository,
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
        val storedConversionsByDate = getStoredConversions(sourceUnit, targetCurrency, dates)
        val notStoredDates = getNotStoredDates(storedConversionsByDate, dates)

        val newConversions = if (notStoredDates.isNotEmpty()) {
            conversionFunction.invoke(sourceUnit, targetCurrency, notStoredDates)
                .also { it.storeConversions() }
        } else {
            emptyList()
        }

        return storedConversionsByDate + newConversions
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
        val pricingInstrument = instrumentConversionInfoRepository.findByInstrument(instrument)

        val conversions = if (pricingInstrument.mainCurrency == currency) {
            getInstrumentMainCurrencyConversions(pricingInstrument, dates)
        } else {
            getInstrumentConversionsWithImplicitConversion(pricingInstrument, currency, dates)
        }
        return conversions
    }

    private suspend fun getInstrumentMainCurrencyConversions(
        pricingInstrument: InstrumentConversionInfo,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val instrumentConverter = instrumentConverterRegistry.getConverter(pricingInstrument)
        return instrumentConverter.convert(pricingInstrument, dates)
    }

    private suspend fun getInstrumentConversionsWithImplicitConversion(
        pricingInstrument: InstrumentConversionInfo,
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

    private suspend fun getStoredConversions(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = conversionRepository
        .getConversions(sourceUnit, targetCurrency, dates)
        .map { ConversionResponse(sourceUnit, it.target, it.date, it.price) }

    private suspend fun List<ConversionResponse>.storeConversions() {
        this.forEach {
            conversionRepository.saveConversion(
                Conversion(it.sourceUnit, it.targetCurrency, it.date, it.rate)
            )
        }
    }

    private fun getNotStoredDates(
        storedConversions: List<ConversionResponse>,
        dates: List<LocalDate>,
    ): List<LocalDate> {
        val storedDates = storedConversions.map { it.date }.toSet()
        return dates.filterNot { it in storedDates }
    }
}
