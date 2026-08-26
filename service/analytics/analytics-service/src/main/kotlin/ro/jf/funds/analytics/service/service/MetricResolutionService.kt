package ro.jf.funds.analytics.service.service

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
        val buckets = request.interval.generateBuckets()
        val collected = linkedMapOf<QueryId, MutableList<QueryBucketValue>>()
        request.queries.forEach { collected[it.id] = mutableListOf() }

        resolveFlow(request).collect { bucketValue ->
            collected.getValue(bucketValue.queryId).add(bucketValue)
        }

        MetricResolutionReport(
            buckets = buckets,
            series = collected.mapValues { (_, bucketValues) ->
                ScalarSeries(bucketValues.associate { it.bucket to it.values })
            },
        )
    }

    fun resolveFlow(request: MetricResolutionRequest): Flow<QueryBucketValue> = channelFlow {
        log.info { "Resolving ${request.queries.size} queries for user ${request.userId}, interval=${request.interval}, targetCurrency=${request.targetCurrency}: ${request.queries}" }
        val buckets = request.interval.generateBuckets()
        val flows = wireNodeFlows(request, buckets, this)

        request.queries.forEach { query ->
            launch {
                // shared flows never signal completion, so collection is bounded by the bucket clock:
                // after one emission per bucket the subscription is cancelled, letting the request scope close
                flows.getValue(nodeKey(query.metric, query.context))
                    .filterIsInstance<SeriesEmission.Bucket>()
                    .take(buckets.size)
                    .collect { emission ->
                        val scalars = emission.value as SeriesSlice.Scalars
                        send(QueryBucketValue(query.id, emission.dateTime, scalars.values))
                    }
            }
        }
    }

    private data class SeriesNode(
        val series: Series<*>,
        val context: QueryContext,
    )

    private fun wireNodeFlows(
        request: MetricResolutionRequest,
        buckets: List<LocalDateTime>,
        scope: CoroutineScope,
    ): Map<SeriesNode, Flow<SeriesEmission>> {
        val flows = mutableMapOf<SeriesNode, Flow<SeriesEmission>>()
        request.queries.forEach { query ->
            for (series in resolutionOrder(query.metric)) {
                val node = nodeKey(series, query.context)
                if (node in flows) continue
                val definition = registry[series]
                val dependencyFlows = definition.dependencies.map { dependency ->
                    dependency to flows.getValue(nodeKey(dependency, query.context))
                }
                flows[node] = nodeFlow(definition, request.resolutionContext(node.context), buckets, dependencyFlows)
                    // shared so fan-out consumers observe a single execution per node; replay buffers the
                    // full emission history (previous seed + one slice per bucket) so consumers subscribing
                    // late or draining slowly never miss slices — keeps diamond topologies deadlock-free
                    .shareIn(scope, SharingStarted.Lazily, replay = buckets.size + 1)
            }
        }
        return flows
    }

    private fun nodeKey(series: Series<*>, context: QueryContext): SeriesNode =
        SeriesNode(series, context.projected(effectiveSensitivity(series)))

    // a node must distinguish every context dimension its dependency closure distinguishes, otherwise
    // one shared node would wire to dependency nodes resolved under another query's context
    private fun effectiveSensitivity(series: Series<*>): Set<ContextDimension> {
        val definition = registry[series]
        return definition.contextSensitivity + definition.dependencies.flatMap { effectiveSensitivity(it) }
    }

    private fun resolutionOrder(requested: Series.Metric): List<Series<*>> {
        val order = mutableListOf<Series<*>>()
        val visited = mutableSetOf<Series<*>>()

        fun visit(series: Series<*>) {
            if (!visited.add(series)) return
            registry[series].dependencies.forEach { visit(it) }
            order.add(series)
        }

        visit(requested)
        return order
    }

    private fun nodeFlow(
        definition: SeriesDefinition<*>,
        context: SeriesResolutionContext,
        buckets: List<LocalDateTime>,
        dependencyFlows: List<Pair<Series<*>, Flow<SeriesEmission>>>,
    ): Flow<SeriesEmission> = flow {
        val resolver = definition.createResolver(context)
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
