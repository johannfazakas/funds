package ro.jf.funds.reporting.service.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.TimeGranularity

fun GranularDateInterval.getBucket(date: LocalDate): DateInterval =
    DateInterval(getBucketStart(date), getBucketEnd(date))

fun GranularDateInterval.getBuckets(): Sequence<DateInterval> =
    generateSequence(
        seed = getFirstBucket(),
        nextFunction = { previous: DateInterval ->
            getNextBucket(previous)
                .takeIf { it.from <= interval.to }
        }
    )

fun GranularDateInterval.getForecastBuckets(outputBuckets: Int): Sequence<DateInterval> =
    generateSequence(
        seed = getNextBucket(getLastBucket()),
        nextFunction = { getNextBucket(it) }
    ).take(outputBuckets)

fun <D> GranularDateInterval.generateBucketedData(
    seedFunction: (DateInterval) -> D,
    nextFunction: (DateInterval, D) -> D,
): Map<DateInterval, D> {
    return generateSequence(
        seedFunction = { getFirstBucket().let { it to seedFunction(it) } },
        nextFunction = { (previousBucket, previousData) ->
            getNextBucket(previousBucket)
                .takeIf { it.from <= interval.to }
                ?.let { it to nextFunction(it, previousData) }
        }
    ).toMap()
}

fun <D> GranularDateInterval.generateForecastData(
    outputBuckets: Int,
    inputBuckets: Int,
    inputDataResolver: (DateInterval) -> D?,
    forecastResolver: (List<D>) -> D,
): Map<DateInterval, D> {
    data class ForecastBucket(
        val forecastBucket: DateInterval,
        val forecastInputBuckets: List<DateInterval>,
        val forecastedData: Map<DateInterval, D>,
    )

    return generateSequence<ForecastBucket>(
        seedFunction = {
            val forecastBucket = getNextBucket(getLastBucket())
            val inputBuckets = generateSequence(
                seed = getLastBucket(),
                nextFunction = { previousBucket ->
                    getPreviousBucket(previousBucket)
                }
            )
                .take(inputBuckets)
                .toList()
                .reversed()
            val forecastedData = inputBuckets
                .mapNotNull { inputDataResolver(it) }
                .let { mapOf(forecastBucket to forecastResolver(it)) }
            ForecastBucket(forecastBucket, inputBuckets, forecastedData)
        },
        nextFunction = { previousForecast ->
            val forecastBucket = getNextBucket(previousForecast.forecastBucket)
            val inputBuckets = previousForecast.forecastInputBuckets
                .drop(1)
                .plus(previousForecast.forecastBucket)
            val forecastedData = inputBuckets
                .mapNotNull { previousForecast.forecastedData[it] ?: inputDataResolver(it) }
                .let { mapOf(forecastBucket to forecastResolver(it)) }
            ForecastBucket(forecastBucket, inputBuckets, previousForecast.forecastedData + forecastedData)
        }
    )
        .take<ForecastBucket>(outputBuckets)
        .lastOrNull<ForecastBucket>()
        ?.forecastedData
        ?: emptyMap()
}

private fun GranularDateInterval.getNextBucket(previousBucket: DateInterval): DateInterval {
    val nextBucketStart = previousBucket.to.plus(DatePeriod(days = 1))
    val nextBucketEnd = getBucketEnd(nextBucketStart)
    return DateInterval(nextBucketStart, nextBucketEnd)
}

private fun GranularDateInterval.getPreviousBucket(nextBucket: DateInterval): DateInterval {
    val previousBucketEnd = nextBucket.from.minus(DatePeriod(days = 1))
    val previousBucketStart = getBucketStart(previousBucketEnd)
    return DateInterval(previousBucketStart, previousBucketEnd)
}

private fun GranularDateInterval.getFirstBucket(): DateInterval =
    DateInterval(getBucketStart(interval.from), getBucketEnd(interval.from))

private fun GranularDateInterval.getLastBucket(): DateInterval =
    DateInterval(getBucketStart(interval.to), getBucketEnd(interval.to))

private fun GranularDateInterval.getBucketStart(date: LocalDate): LocalDate {
    val bucketLogicalStart = when (granularity) {
        TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
        TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
        TimeGranularity.YEARLY -> LocalDate(date.year, 1, 1)
    }
    return bucketLogicalStart
}

private fun GranularDateInterval.getBucketEnd(date: LocalDate): LocalDate {
    return when (granularity) {
        TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
        TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
            .plus(DatePeriod(months = 1))
            .minus(DatePeriod(days = 1))

        TimeGranularity.YEARLY -> LocalDate(date.year, 12, 31)
    }
}
