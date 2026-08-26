package ro.jf.funds.analytics.service.service.series

import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.SeriesSlice

class SeriesRegistryTest {

    private object StubResolver : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY
    }

    private fun definition(metric: Series.Metric, vararg dependencies: Series<*>) =
        object : SeriesDefinition<SeriesSlice.Scalars>(metric, dependencies.toList()) {
            override fun createResolver(request: MetricResolutionRequest) = StubResolver
        }

    @Test
    fun `given metric depending on unregistered metric - when creating registry - then fails naming metric and dependency`() {
        assertThatThrownBy {
            SeriesRegistry(listOf(definition(Series.TotalProfit, Series.TotalInstrumentValue)))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("TotalProfit")
            .hasMessageContaining("TotalInstrumentValue")
    }

    @Test
    fun `given cyclic metric dependencies - when creating registry - then fails naming the cycle`() {
        assertThatThrownBy {
            SeriesRegistry(
                listOf(
                    definition(Series.Balance, Series.NetChange),
                    definition(Series.NetChange, Series.TotalProfit),
                    definition(Series.TotalProfit, Series.Balance),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cycle")
            .hasMessageContaining("Balance")
            .hasMessageContaining("NetChange")
            .hasMessageContaining("TotalProfit")
    }

    @Test
    fun `given duplicate metric definitions - when creating registry - then fails naming the duplicate`() {
        assertThatThrownBy {
            SeriesRegistry(listOf(definition(Series.Balance), definition(Series.Balance)))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Balance")
    }

    @Test
    fun `given valid acyclic definitions - when creating registry - then definitions are retrievable by metric`() {
        val registry = SeriesRegistry(
            listOf(
                definition(Series.NetChange),
                definition(Series.Balance, Series.NetChange),
            )
        )

        assertThat(registry[Series.Balance].dependencies).containsExactly(Series.NetChange)
        assertThatThrownBy { registry[Series.TotalProfit] }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("TotalProfit")
    }
}
