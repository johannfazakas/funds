package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed class ReportDataInterval() {
    abstract val fromDate: LocalDate
    abstract val toDate: LocalDate
    abstract val forecastUntilDate: LocalDate?

    abstract fun getBucket(date: LocalDate): TimeBucket

    fun getBuckets(): Sequence<TimeBucket> = generateSequence(
        seed = getBucket(fromDate),
        nextFunction = { previous: TimeBucket -> getNextBucket(previous) }
    ).takeWhile { it.from <= toDate }

    fun getFirstBucket(): TimeBucket = getBucket(fromDate)
    fun getLastBucket(): TimeBucket = getBucket(toDate)

    fun getNextBucket(previous: TimeBucket): TimeBucket =
        getBucket(previous.to.plus(1, DateTimeUnit.DAY))

    fun getPreviousBucket(previous: TimeBucket): TimeBucket =
        getBucket(previous.from.minus(1, DateTimeUnit.DAY))

    fun <D> generateBucketedData(
        seedFunction: (TimeBucket) -> D,
        nextFunction: (TimeBucket, D) -> D,
    ): Map<TimeBucket, D> {
        return generateSequence(
            seedFunction = { getFirstBucket().let { it to seedFunction(it) } },
            nextFunction = { (previousBucket, previousData) ->
                getNextBucket(previousBucket)
                    .takeIf { it.from <= toDate }
                    ?.let { it to nextFunction(it, previousData) }
            }
        ).toMap()
    }

    fun <D> generateForecastData(
        inputBuckets: Int,
        inputDataResolver: (TimeBucket) -> D?,
        forecastResolver: (List<D>) -> D,
    ): Map<TimeBucket, D> {
        data class ForecastBucket(
            val forecastBucket: TimeBucket,
            val forecastInputBuckets: List<TimeBucket>,
            val forecastedData: Map<TimeBucket, D>,
        )
        // TODO(Johann-29?) could this be refactored? isn't this actually a fold?
        val forecastUntilDate = forecastUntilDate ?: return emptyMap()
        return generateSequence<ForecastBucket>(
            seedFunction = {
                val forecastBucket = getNextBucket(getLastBucket())
                val inputBuckets = generateSequence(
                    seed = getLastBucket(),
                    nextFunction = { previousBucket -> getPreviousBucket(previousBucket) }
                )
                    .take(inputBuckets)
                    .toList()
                    .reversed()
                val forecastedData = inputBuckets
                    .mapNotNull { inputDataResolver(it) }
                    .let { mapOf(forecastBucket to forecastResolver(it)) }
                ForecastBucket(forecastBucket, inputBuckets, forecastedData)
            },
            nextFunction = { previousForecast: ForecastBucket ->
                val forecastBucket = getNextBucket(previousForecast.forecastBucket)
                val inputBuckets = previousForecast.forecastInputBuckets
                    .takeLast(inputBuckets - 1)
                    .plus(previousForecast.forecastBucket)
                val forecastedData = inputBuckets
                    .mapNotNull { previousForecast.forecastedData[it] ?: inputDataResolver(it) }
                    .let { mapOf(forecastBucket to forecastResolver(it)) }
                ForecastBucket(forecastBucket, inputBuckets, previousForecast.forecastedData + forecastedData)
            }
        )
            .takeWhile { it.forecastBucket.from <= forecastUntilDate }
            .lastOrNull<ForecastBucket>()
            ?.forecastedData
            ?: emptyMap()
    }

    fun getForecastBuckets(): Sequence<TimeBucket> {
        val untilDate = forecastUntilDate ?: return emptySequence()
        return generateSequence(
            seed = getBucket(toDate.plus(1, DateTimeUnit.DAY)),
            nextFunction = { it.to.plus(1, DateTimeUnit.DAY).let { nextDate -> getBucket(nextDate) } }
        ).takeWhile { it.from <= untilDate }
    }

    data class Daily(
        override val fromDate: LocalDate,
        override val toDate: LocalDate,
        override val forecastUntilDate: LocalDate? = null,
    ) : ReportDataInterval() {
        init {
            require(fromDate <= toDate) { "From date must be before or equal to the to date. from = $fromDate, to = $toDate." }
            forecastUntilDate?.let {
                require(it >= toDate) { "Forecast until date must be after or equal to the to date. forecastUntil = $it, to = $toDate." }
            }
        }

        override fun getBucket(date: LocalDate): TimeBucket = TimeBucket(from = date, to = date)
    }

    data class Monthly(
        val fromYearMonth: YearMonth,
        val toYearMonth: YearMonth,
        val forecastUntilYearMonth: YearMonth? = null,
    ) : ReportDataInterval() {
        init {
            require(fromYearMonth <= toYearMonth) {
                "From year month must be before or equal to the to year month. from = $fromYearMonth, to = $toYearMonth."
            }
            forecastUntilYearMonth?.let {
                require(it >= toYearMonth) {
                    "Forecast until year month must be after or equal to the to year month. forecastUntil = $it, to = $toYearMonth."
                }
            }
        }

        override val toDate: LocalDate =
            LocalDate(toYearMonth.year, toYearMonth.month, 1)
                .plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY)
        override val fromDate: LocalDate = LocalDate(fromYearMonth.year, fromYearMonth.month, 1)
        override val forecastUntilDate: LocalDate? = forecastUntilYearMonth?.let {
            LocalDate(it.year, it.month, 1)
                .plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY)
        }

        override fun getBucket(date: LocalDate): TimeBucket = TimeBucket(
            from = LocalDate(date.year, date.month, 1),
            to = LocalDate(date.year, date.month, 1)
                .plus(1, DateTimeUnit.MONTH)
                .minus(1, DateTimeUnit.DAY)
        )
    }

    data class Yearly(
        val fromYear: Int,
        val toYear: Int,
        val forecastUntilYear: Int? = null,
    ) : ReportDataInterval() {
        init {
            require(fromYear <= toYear) {
                "From year must be before or equal to the to year. from = $fromYear, to = $toYear."
            }
            forecastUntilYear?.let {
                require(it >= toYear) {
                    "Forecast until year must be after or equal to the to year. forecastUntil = $it, to = $toYear."
                }
            }
        }

        override val toDate: LocalDate = LocalDate(toYear, 12, 31)
        override val fromDate: LocalDate = LocalDate(fromYear, 1, 1)
        override val forecastUntilDate: LocalDate? = forecastUntilYear?.let { LocalDate(it, 12, 31) }

        override fun getBucket(date: LocalDate): TimeBucket = TimeBucket(
            from = LocalDate(date.year, 1, 1),
            to = LocalDate(date.year, 12, 31)
        )
    }
}
