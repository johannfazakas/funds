package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.MetricResolutionService
import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.ScalarSeries
import ro.jf.funds.analytics.service.domain.SeriesSlice
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.platform.api.model.Currency
import java.util.concurrent.atomic.AtomicInteger

class MetricResolutionServiceTest {

    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-04-01T00:00:00"),
    )
    private val buckets = listOf(
        LocalDateTime.parse("2024-01-01T00:00:00"),
        LocalDateTime.parse("2024-02-01T00:00:00"),
        LocalDateTime.parse("2024-03-01T00:00:00"),
    )

    private fun request(metrics: List<Series.Metric>) = MetricResolutionRequest(
        userId = uuid4(),
        interval = interval,
        targetCurrency = Currency.RON,
        metrics = metrics,
    )

    private fun scalars(value: Int) =
        SeriesSlice.Scalars(mapOf(GroupKey.Ungrouped to BigDecimal.fromInt(value)))

    private fun scalarResolver(
        onPrevious: suspend () -> Unit = {},
        onBucket: suspend (LocalDateTime, DependencySlices) -> SeriesSlice.Scalars,
    ) = object : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            onPrevious()
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars =
            onBucket(bucket, inputs)
    }

    private fun passThrough(extract: (DependencySlices) -> SeriesSlice.Scalars) =
        scalarResolver { _, inputs -> extract(inputs) }

    private fun testDefinition(
        metric: Series.Metric,
        dependencies: List<Series<*>> = emptyList(),
        factory: (MetricResolutionRequest) -> SeriesBucketResolver<SeriesSlice.Scalars>,
    ) = object : SeriesDefinition<SeriesSlice.Scalars>(metric, dependencies) {
        override fun createResolver(request: MetricResolutionRequest) = factory(request)
    }

    @Test
    fun `given two metrics sharing a dependency - when resolving both - then shared dependency is resolved exactly once`(): Unit =
        runBlocking {
            val leafResolutions = AtomicInteger(0)
            val registry = SeriesRegistry(
                listOf(
                    testDefinition(Series.TotalProfit) {
                        scalarResolver(onPrevious = { leafResolutions.incrementAndGet() }) { _, _ -> scalars(1) }
                    },
                    testDefinition(Series.Balance, listOf(Series.TotalProfit)) {
                        passThrough { it[Series.TotalProfit] }
                    },
                    testDefinition(Series.NetChange, listOf(Series.TotalProfit)) {
                        passThrough { it[Series.TotalProfit] }
                    },
                )
            )

            val report = MetricResolutionService(registry)
                .resolve(request(listOf(Series.Balance, Series.NetChange)))

            assertThat(leafResolutions.get()).isEqualTo(1)
            assertThat(report.series.keys).containsExactly(Series.Balance, Series.NetChange)
            assertThat(report[Series.Balance].value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.ONE)
        }

    @Test
    fun `given metric deriving from previous bucket values - when resolving - then resolver state carries across buckets`(): Unit =
        runBlocking {
            val registry = SeriesRegistry(
                listOf(
                    testDefinition(Series.TotalProfit) {
                        scalarResolver { bucket, _ -> scalars(10 * (buckets.indexOf(bucket) + 1)) }
                    },
                    testDefinition(Series.Balance, listOf(Series.TotalProfit)) {
                        val totalProfit: (DependencySlices) -> SeriesSlice.Scalars = { it[Series.TotalProfit] }
                        object : SeriesBucketResolver<SeriesSlice.Scalars> {
                            private var lastSeen: BigDecimal = BigDecimal.ZERO
                            override suspend fun resolvePrevious(previous: DependencySlices) = SeriesSlice.Scalars.EMPTY
                            override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
                                val emitted = SeriesSlice.Scalars(mapOf(GroupKey.Ungrouped to lastSeen))
                                lastSeen = totalProfit(inputs).values.getValue(GroupKey.Ungrouped)
                                return emitted
                            }
                        }
                    },
                )
            )

            val report = MetricResolutionService(registry).resolve(request(listOf(Series.Balance)))

            assertThat(report.buckets).isEqualTo(buckets)
            val shifted = report[Series.Balance]
            assertThat(shifted.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.ZERO)
            assertThat(shifted.value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.fromInt(10))
            assertThat(shifted.value("2024-03-01T00:00:00")).isEqualTo(BigDecimal.fromInt(20))
        }

    @Test
    fun `given a metric chain - when resolving - then parent buckets are emitted before dependencies finish later buckets`(): Unit =
        runBlocking {
            val firstParentBucketReceived = CompletableDeferred<Unit>()
            val registry = SeriesRegistry(
                listOf(
                    testDefinition(Series.TotalProfit) {
                        scalarResolver { bucket, _ ->
                            if (bucket == buckets[2]) firstParentBucketReceived.await()
                            scalars(1)
                        }
                    },
                    testDefinition(Series.Balance, listOf(Series.TotalProfit)) {
                        passThrough { it[Series.TotalProfit] }
                    },
                )
            )

            withTimeout(5_000) {
                MetricResolutionService(registry).resolveFlow(request(listOf(Series.Balance)))
                    .collect { bucketValue ->
                        if (bucketValue.bucket == buckets[0]) firstParentBucketReceived.complete(Unit)
                    }
            }

            assertThat(firstParentBucketReceived.isCompleted).isTrue()
        }

    @Test
    fun `given a failing resolver - when resolving - then the resolution fails and other nodes are cancelled`(): Unit =
        runBlocking {
            val registry = SeriesRegistry(
                listOf(
                    testDefinition(Series.TotalProfit) {
                        scalarResolver { bucket, _ ->
                            if (bucket == buckets[1]) throw IllegalStateException("conversion failed")
                            scalars(1)
                        }
                    },
                    testDefinition(Series.Balance, listOf(Series.TotalProfit)) {
                        passThrough { it[Series.TotalProfit] }
                    },
                )
            )

            assertThatThrownBy {
                runBlocking { MetricResolutionService(registry).resolve(request(listOf(Series.Balance))) }
            }.hasMessageContaining("conversion failed")
        }

    @Test
    fun `given stateful resolvers - when resolving twice - then state does not leak between resolutions`(): Unit =
        runBlocking {
            val registry = SeriesRegistry(
                listOf(
                    testDefinition(Series.Balance) {
                        object : SeriesBucketResolver<SeriesSlice.Scalars> {
                            private var counter = 0
                            override suspend fun resolvePrevious(previous: DependencySlices) = SeriesSlice.Scalars.EMPTY
                            override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices) =
                                scalars(++counter)
                        }
                    },
                )
            )
            val service = MetricResolutionService(registry)

            val first = service.resolve(request(listOf(Series.Balance)))
            val second = service.resolve(request(listOf(Series.Balance)))

            assertThat(first[Series.Balance].value("2024-03-01T00:00:00")).isEqualTo(BigDecimal.fromInt(3))
            assertThat(second[Series.Balance].value("2024-03-01T00:00:00")).isEqualTo(BigDecimal.fromInt(3))
        }

    private fun ScalarSeries.value(bucket: String): BigDecimal =
        this[LocalDateTime.parse(bucket), GroupKey.Ungrouped]
}
