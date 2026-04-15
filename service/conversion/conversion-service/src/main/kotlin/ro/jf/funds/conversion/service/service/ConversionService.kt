package ro.jf.funds.conversion.service.service

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.service.domain.Conversion
import ro.jf.funds.conversion.service.domain.ConversionExceptions
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.persistence.ConversionRepository
import ro.jf.funds.conversion.service.service.currency.CurrencyConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConversionInfoRepository
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument

private const val MAX_FALLBACK_DAYS = 15

class ConversionService(
    private val conversionRepository: ConversionRepository,
    private val currencyConverter: CurrencyConverter,
    private val instrumentConversionInfoRepository: InstrumentConversionInfoRepository,
    private val instrumentConverterRegistry: InstrumentConverterRegistry,
) {
    private data class ResolvedConversion(
        val response: ConversionResponse,
        val fallback: Boolean = false,
    )

    suspend fun convert(request: ConversionsRequest): ConversionsResponse {
        return request.conversions
            .groupBy { it.sourceUnit to it.targetCurrency }
            .flatMap { (pair, requests) ->
                convert(pair.first, pair.second, requests.map { it.date }.toSet())
            }
            .let { ConversionsResponse(it) }
    }

    private suspend fun convert(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
    ): List<ConversionResponse> {
        val stored = loadStoredConversions(sourceUnit, targetCurrency, dates)
        val missingDates = dates - stored.dates()
        if (missingDates.isEmpty()) return stored.map { it.response }

        val resolved = resolveConversions(sourceUnit, targetCurrency, missingDates)
        resolved.storeNonFallbackConversions()

        return (stored + resolved).map { it.response }
    }

    private suspend fun resolveConversions(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> = when (sourceUnit) {
        is Currency -> resolveCurrencyConversions(sourceUnit, targetCurrency, dates)
        is Instrument -> resolveInstrumentConversions(sourceUnit, targetCurrency, dates)
    }

    private suspend fun resolveCurrencyConversions(
        source: Currency,
        target: Currency,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> {
        val fetched = currencyConverter.convert(source, target, dates.toList())
            .map { ResolvedConversion(it) }
        val missingDates = dates - fetched.dates()
        val fallbacks = resolveFallbackConversions(source, target, missingDates, fetched) { fallbackDates ->
            currencyConverter.convert(source, target, fallbackDates.toList()).map { ResolvedConversion(it) }
        }
        return fetched + fallbacks
    }

    private suspend fun resolveInstrumentConversions(
        instrument: Instrument,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> {
        val info = instrumentConversionInfoRepository.findByInstrument(instrument)
        return if (info.mainCurrency == targetCurrency) {
            resolveDirectInstrumentConversions(info, dates)
        } else {
            resolveImplicitInstrumentConversions(info, targetCurrency, dates)
        }
    }

    private suspend fun resolveDirectInstrumentConversions(
        info: InstrumentConversionInfo,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> {
        val converter = instrumentConverterRegistry.getConverter(info)
        val fetched = converter.convert(info, dates.toList()).map { ResolvedConversion(it) }
        val missingDates = dates - fetched.dates()
        val fallbacks =
            resolveFallbackConversions(info.instrument, info.mainCurrency, missingDates, fetched) { fallbackDates ->
                converter.convert(info, fallbackDates.toList()).map { ResolvedConversion(it) }
            }
        return fetched + fallbacks
    }

    private suspend fun resolveImplicitInstrumentConversions(
        info: InstrumentConversionInfo,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> {
        val instrumentByDate =
            resolvePartialConversions(info.instrument, info.mainCurrency, dates) { missingDates ->
                resolveDirectInstrumentConversions(info, missingDates)
            }.associateBy { it.response.date }
        val currencyByDate =
            resolvePartialConversions(info.mainCurrency, targetCurrency, dates) { missingDates ->
                resolveCurrencyConversions(info.mainCurrency, targetCurrency, missingDates)
            }.associateBy { it.response.date }

        return dates.mapNotNull { date ->
            val instrRate = instrumentByDate[date] ?: return@mapNotNull null
            val currRate = currencyByDate[date] ?: return@mapNotNull null
            ResolvedConversion(
                response = ConversionResponse(
                    sourceUnit = info.instrument,
                    targetCurrency = targetCurrency,
                    date = date,
                    rate = instrRate.response.rate * currRate.response.rate,
                ),
                fallback = instrRate.fallback || currRate.fallback,
            )
        }
    }

    private suspend fun resolvePartialConversions(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
        resolveFunction: suspend (Set<LocalDate>) -> List<ResolvedConversion>,
    ): List<ResolvedConversion> {
        val stored = loadStoredConversions(sourceUnit, targetCurrency, dates)
        val missingDates = dates - stored.dates()
        if (missingDates.isEmpty()) return stored

        val resolved = resolveFunction(missingDates)
        resolved.storeNonFallbackConversions()

        return stored + resolved
    }

    private suspend fun loadStoredConversions(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        dates: Set<LocalDate>,
    ): List<ResolvedConversion> = conversionRepository
        .getConversions(sourceUnit, targetCurrency, dates.toList())
        .map { ResolvedConversion(ConversionResponse(sourceUnit, it.target, it.date, it.price)) }

    private suspend fun resolveFallbackConversions(
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        missingDates: Set<LocalDate>,
        resolved: List<ResolvedConversion>,
        fetchFn: suspend (Set<LocalDate>) -> List<ResolvedConversion>,
    ): List<ResolvedConversion> {
        if (missingDates.isEmpty()) return emptyList()

        val resolvedByDate = resolved.associateBy { it.response.date }

        return missingDates.map { missingDate ->
            val lookbackDates = missingDate.lookbackDates()

            val fallbackRate = lookbackDates
                .firstNotNullOfOrNull { resolvedByDate[it] }?.response?.rate
                ?: conversionRepository.getConversions(sourceUnit, targetCurrency, lookbackDates)
                    .maxByOrNull { it.date }?.price
                ?: fetchFn(lookbackDates.toSet())
                    .maxByOrNull { it.response.date }
                    ?.response?.rate
                ?: throw ConversionExceptions.ConversionNotFound(sourceUnit, targetCurrency, missingDate)

            ResolvedConversion(
                response = ConversionResponse(sourceUnit, targetCurrency, missingDate, fallbackRate),
                fallback = true,
            )
        }
    }

    private fun List<ResolvedConversion>.dates(): Set<LocalDate> =
        map { it.response.date }.toSet()

    private fun LocalDate.lookbackDates(): List<LocalDate> =
        (1..MAX_FALLBACK_DAYS).map { this.minus(it, DateTimeUnit.DAY) }

    private suspend fun List<ResolvedConversion>.storeNonFallbackConversions() {
        this.filter { !it.fallback }.forEach {
            conversionRepository.saveConversion(
                Conversion(it.response.sourceUnit, it.response.targetCurrency, it.response.date, it.response.rate)
            )
        }
    }
}
