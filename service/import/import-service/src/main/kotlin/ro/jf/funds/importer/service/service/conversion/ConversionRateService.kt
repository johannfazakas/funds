package ro.jf.funds.importer.service.service.conversion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal

class ConversionRateService(
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun getConversionRates(
        conversions: List<Conversion>,
    ): Store<Conversion, BigDecimal> = withContext(Dispatchers.IO) {
        if (conversions.isEmpty())
            Store(emptyMap())
        else
            conversions
                .groupBy({ it.sourceCurrency to it.targetCurrency }) { it.date }
                .map { (financialUnits, dates) ->
                    async {
                        getConversionRates(financialUnits.first, financialUnits.second, dates)
                    }
                }
                .map { it.await() }
                .reduce { acc, map -> acc + map }
                .let { Store(it) }
    }

    private suspend fun getConversionRates(
        sourceUnit: FinancialUnit, targetUnit: FinancialUnit, dates: List<LocalDate>,
    ): Map<Conversion, BigDecimal> {
        if (sourceUnit !is Currency || targetUnit !is Currency) {
            throw ImportDataException("Conversion between non-currency units is not supported. yet")
        }
        val historicalPrices = getHistoricalPrices(sourceUnit, targetUnit, dates.distinct())
        checkMissingDates(sourceUnit, targetUnit, dates, historicalPrices.map { it.date })
        return historicalPrices.associate {
            Conversion(it.date, sourceUnit, targetUnit) to it.price
        }
    }

    private fun checkMissingDates(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        requestDates: List<LocalDate>,
        responseDates: List<LocalDate>,
    ) {
        val missingDates = requestDates.toSet() - responseDates.toSet()
        if (missingDates.isNotEmpty()) {
            throw ImportDataException("Missing historical prices for conversion: $sourceCurrency to $targetCurrency on dates: $missingDates")
        }
    }

    private suspend fun getHistoricalPrices(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<HistoricalPrice> {
        return withContext(Dispatchers.IO) {
            historicalPricingSdk.convertCurrency(
                sourceCurrency,
                targetCurrency,
                dates
            )
        }
    }
}
