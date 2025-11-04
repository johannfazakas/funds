package ro.jf.funds.reporting.service.domain

import kotlinx.coroutines.flow.*
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

    fun getPreviousLastDay(): LocalDate = fromDate.minus(1, DateTimeUnit.DAY)

    suspend fun <D> generateBucketedData(
        function: suspend (TimeBucket) -> D,
    ): Map<TimeBucket, D> =
        generateSequence(getFirstBucket()) { getNextBucket(it) }
            .takeWhile { it.from <= toDate }
            .asFlow()
            .map { bucket -> bucket to function(bucket) }
            .toList().toMap()

    suspend fun <D> generateBucketedData(
        previousData: D,
        function: suspend (TimeBucket, D) -> D,
    ): Map<TimeBucket, D> {
        return generateFlow(
            seed = { getFirstBucket().let { firstBucket -> firstBucket to function(firstBucket, previousData) } },
            next = { (previousBucket, previousData) ->
                getNextBucket(previousBucket)
                    .takeIf { it.from <= toDate }
                    ?.let { it to function(it, previousData) }
            },
        )
            .toList().toMap()
    }

    private fun <D : Any> generateFlow(
        seed: suspend () -> D,
        next: suspend (D) -> D?,
    ): Flow<D> = flow {
        var value: D? = seed()
        while (value != null) {
            emit(value)
            value = next(value)
        }
    }

    fun <D> generateForecastData(
        inputBuckets: Int,
        realData: ByBucket<D>,
        forecastResolver: (List<D>, TimeBucket) -> D,
    ): Map<TimeBucket, D> {
        val relevantTailBuckets = realData.entries
            .sortedBy { it.key.from }
            .takeLast(inputBuckets)
            .map { it.value }
            .toList()

        val forecastUntil = forecastUntilDate ?: return emptyMap()
        return generateSequence(getNextBucket(getLastBucket())) { getNextBucket(it) }
            .takeWhile { it.from < forecastUntil }
            .fold(relevantTailBuckets to mapOf<TimeBucket, D>()) { (relevantBuckets, forecast), bucket ->
                val forecastData: D = forecastResolver(relevantBuckets, bucket)
                (relevantBuckets + forecastData).takeLast(inputBuckets) to forecast + (bucket to forecastData)
            }
            .second
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
