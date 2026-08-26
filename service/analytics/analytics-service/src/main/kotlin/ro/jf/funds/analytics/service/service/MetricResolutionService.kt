package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.service.series.SeriesDefinition
import ro.jf.funds.analytics.service.service.series.SeriesRegistry

private val log = logger { }

class MetricResolutionService(
    private val registry: SeriesRegistry,
) {
    suspend fun resolve(request: MetricResolutionRequest): MetricResolutionReport = coroutineScope {
        val buckets = request.interval.generateBucketedData { it }
        val collected = linkedMapOf<Series.Metric, MutableMap<LocalDateTime, Map<GroupKey, BigDecimal>>>()
        request.metrics.distinct().forEach { collected[it] = mutableMapOf() }

        resolveFlow(request).collect { bucketValue ->
            collected.getValue(bucketValue.metric)[bucketValue.bucket] = bucketValue.values
        }

        MetricResolutionReport(
            buckets = buckets,
            series = collected.mapValues { (_, byBucket) -> ScalarSeries(byBucket) },
        )
    }

    fun resolveFlow(request: MetricResolutionRequest): Flow<MetricBucketValue> = channelFlow {
        log.info { "Resolving metrics ${request.metrics} for user ${request.userId}, interval=${request.interval}, targetCurrency=${request.targetCurrency}, grouping=${request.grouping}" }
        val buckets = request.interval.generateBucketedData { it }
        val requested = request.metrics.distinct()
        val flows = wireNodeFlows(requested, request, buckets, this)

        requested.forEach { metric ->
            launch {
                // shared flows never signal completion, so collection is bounded by the bucket clock:
                // after one emission per bucket the subscription is cancelled, letting the request scope close
                flows.getValue(metric)
                    .filterIsInstance<SeriesEmission.Bucket>()
                    .take(buckets.size)
                    .collect { emission ->
                        val scalars = emission.value as SeriesSlice.Scalars
                        send(MetricBucketValue(metric, emission.dateTime, scalars.values))
                    }
            }
        }
    }

    private fun wireNodeFlows(
        requested: List<Series.Metric>,
        request: MetricResolutionRequest,
        buckets: List<LocalDateTime>,
        scope: CoroutineScope,
    ): Map<Series<*>, Flow<SeriesEmission>> {
        val flows = mutableMapOf<Series<*>, Flow<SeriesEmission>>()
        for (series in resolutionOrder(requested)) {
            val definition = registry[series]
            val dependencyFlows = definition.dependencies.map { it to flows.getValue(it) }
            flows[series] = nodeFlow(definition, request, buckets, dependencyFlows)
                // shared so fan-out consumers observe a single execution per node; replay buffers the
                // full emission history (previous seed + one slice per bucket) so consumers subscribing
                // late or draining slowly never miss slices — keeps diamond topologies deadlock-free
                .shareIn(scope, SharingStarted.Lazily, replay = buckets.size + 1)
        }
        return flows
    }

    private fun resolutionOrder(requested: List<Series.Metric>): List<Series<*>> {
        val order = mutableListOf<Series<*>>()
        val visited = mutableSetOf<Series<*>>()

        fun visit(series: Series<*>) {
            if (!visited.add(series)) return
            registry[series].dependencies.forEach { visit(it) }
            order.add(series)
        }

        requested.forEach { visit(it) }
        return order
    }

    private fun nodeFlow(
        definition: SeriesDefinition<*>,
        request: MetricResolutionRequest,
        buckets: List<LocalDateTime>,
        dependencyFlows: List<Pair<Series<*>, Flow<SeriesEmission>>>,
    ): Flow<SeriesEmission> = flow {
        val resolver = definition.createResolver(request)
        if (dependencyFlows.isEmpty()) {
            emit(SeriesEmission.Previous(resolver.resolvePrevious(DependencySlices.EMPTY)))
            for (bucket in buckets) {
                emit(SeriesEmission.Bucket(bucket, resolver.resolveBucket(bucket, DependencySlices.EMPTY)))
            }
        } else coroutineScope {
            // each dependency flow is collected into a channel so its emissions can be pulled one at a
            // time with receive(); pulling one element from every channel per step is what zips the
            // dependencies bucket by bucket on the shared clock
            val channels = dependencyFlows.map { (series, dependencyFlow) -> series to dependencyFlow.produceIn(this) }
            try {
                val previousSlices = DependencySlices(channels.associate { (series, channel) ->
                    val emission = channel.receive()
                    check(emission is SeriesEmission.Previous) {
                        "Series '${definition.series}' expected previous emission from '$series' but received $emission"
                    }
                    series to emission.value
                })
                emit(SeriesEmission.Previous(resolver.resolvePrevious(previousSlices)))
                for (bucket in buckets) {
                    val inputs = DependencySlices(channels.associate { (series, channel) ->
                        val emission = channel.receive()
                        check(emission is SeriesEmission.Bucket && emission.dateTime == bucket) {
                            "Series '${definition.series}' expected bucket $bucket from '$series' but received $emission"
                        }
                        series to emission.value
                    })
                    emit(SeriesEmission.Bucket(bucket, resolver.resolveBucket(bucket, inputs)))
                }
            } finally {
                channels.forEach { (_, channel) -> channel.cancel() }
            }
        }
    }
}
