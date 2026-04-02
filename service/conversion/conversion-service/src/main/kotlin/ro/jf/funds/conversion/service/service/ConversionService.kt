package ro.jf.funds.conversion.service.service

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.service.domain.Conversion
import ro.jf.funds.conversion.service.domain.ConversionExceptions
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.persistence.ConversionRepository
import ro.jf.funds.conversion.service.service.currency.CurrencyConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.conversion.service.service.instrument.InstrumentConversionInfoRepository

private const val MAX_FALLBACK_DAYS = 15

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

        val resolvedConversions = storedConversionsByDate + newConversions
        val fallbackFilledConversions = fillMissingDatesWithFallback(sourceUnit, targetCurrency, dates, resolvedConversions)

        return resolvedConversions + fallbackFilledConversions
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
            .mapNotNull { conversionResponse ->
                val conversionRate = currencyConversions[conversionResponse.date] ?: return@mapNotNull null
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

    private suspend fun fillMissingDatesWithFallback(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        requestedDates: List<LocalDate>,
        resolvedConversions: List<ConversionResponse>,
    ): List<ConversionResponse> {
        val resolvedDates = resolvedConversions.map { it.date }.toSet()
        val missingDates = requestedDates.filter { it !in resolvedDates }
        if (missingDates.isEmpty()) return emptyList()

        val resolvedByDate = resolvedConversions.associateBy { it.date }
        return missingDates.map { missingDate ->
            val fallback = (1..MAX_FALLBACK_DAYS).firstNotNullOfOrNull { daysBack ->
                val fallbackDate = missingDate.minus(daysBack, DateTimeUnit.DAY)
                resolvedByDate[fallbackDate]
                    ?: conversionRepository.getConversion(sourceUnit, targetCurrency, fallbackDate)
                        ?.let { ConversionResponse(sourceUnit, it.target, it.date, it.price) }
            } ?: throw ConversionExceptions.ConversionNotFound(sourceUnit, targetCurrency, missingDate)
            ConversionResponse(sourceUnit, targetCurrency, missingDate, fallback.rate)
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
