package ro.jf.funds.analytics.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.platform.api.model.Currency

class MetricsReportRequestTOTest {

    private fun request(
        queries: List<MetricQueryTO> = listOf(MetricQueryTO(id = "q1", metric = MetricTO.BALANCE)),
        from: String = "2024-01-01T00:00:00",
        to: String = "2024-04-01T00:00:00",
    ) = MetricsReportRequestTO(
        interval = ReportIntervalTO(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse(from),
            to = LocalDateTime.parse(to),
        ),
        targetCurrency = Currency.RON,
        queries = queries,
    )

    @Test
    fun `given empty query list - when creating request - then fails`() {
        assertThatThrownBy { request(queries = emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query")
    }

    @Test
    fun `given duplicate query ids - when creating request - then fails`() {
        assertThatThrownBy {
            request(
                queries = listOf(
                    MetricQueryTO(id = "q1", metric = MetricTO.BALANCE),
                    MetricQueryTO(id = "q1", metric = MetricTO.TOTAL_PROFIT),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("q1")
    }

    @Test
    fun `given blank query id - when creating query - then fails`() {
        assertThatThrownBy { MetricQueryTO(id = " ", metric = MetricTO.BALANCE) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("id")
    }

    @Test
    fun `given interval start after end - when creating interval - then fails`() {
        assertThatThrownBy { request(from = "2024-04-01T00:00:00", to = "2024-01-01T00:00:00") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("interval")
    }

    @Test
    fun `given empty query list in payload - when deserializing request - then fails`() {
        val payload = """
            {"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00","to":"2024-04-01T00:00:00"},"targetCurrency":"RON","queries":[]}
        """.trimIndent()

        assertThatThrownBy { Json.decodeFromString<MetricsReportRequestTO>(payload) }
            .hasMessageContaining("query")
    }

    @Test
    fun `given valid payload - when deserializing request - then parses queries and nested interval`() {
        val payload = """
            {"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00","to":"2024-04-01T00:00:00"},"targetCurrency":"RON","queries":[{"id":"q1","metric":"BALANCE","grouping":"FUND","filter":{"fundIds":[]}},{"id":"q2","metric":"TOTAL_PROFIT"}]}
        """.trimIndent()

        val request = Json.decodeFromString<MetricsReportRequestTO>(payload)

        assertThat(request.queries.map { it.id }).containsExactly("q1", "q2")
        assertThat(request.queries.map { it.metric }).containsExactly(MetricTO.BALANCE, MetricTO.TOTAL_PROFIT)
        assertThat(request.interval.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(request.queries[0].grouping).isEqualTo(GroupingCriteria.FUND)
        assertThat(request.queries[1].grouping).isNull()
    }
}
