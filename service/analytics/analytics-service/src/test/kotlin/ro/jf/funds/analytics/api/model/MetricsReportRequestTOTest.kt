package ro.jf.funds.analytics.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.platform.api.model.Currency

class MetricsReportRequestTOTest {

    private fun request(
        metrics: List<MetricTO> = listOf(MetricTO.BALANCE),
        from: String = "2024-01-01T00:00:00",
        to: String = "2024-04-01T00:00:00",
    ) = MetricsReportRequestTO(
        metrics = metrics,
        interval = ReportIntervalTO(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse(from),
            to = LocalDateTime.parse(to),
        ),
        targetCurrency = Currency.RON,
    )

    @Test
    fun `given empty metric list - when creating request - then fails`() {
        assertThatThrownBy { request(metrics = emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("metric")
    }

    @Test
    fun `given interval start after end - when creating interval - then fails`() {
        assertThatThrownBy { request(from = "2024-04-01T00:00:00", to = "2024-01-01T00:00:00") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("interval")
    }

    @Test
    fun `given empty metric list in payload - when deserializing request - then fails`() {
        val payload = """
            {"metrics":[],"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00","to":"2024-04-01T00:00:00"},"targetCurrency":"RON"}
        """.trimIndent()

        assertThatThrownBy { Json.decodeFromString<MetricsReportRequestTO>(payload) }
            .hasMessageContaining("metric")
    }

    @Test
    fun `given valid payload - when deserializing request - then parses metrics and nested interval`() {
        val payload = """
            {"metrics":["BALANCE","TOTAL_PROFIT"],"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00","to":"2024-04-01T00:00:00"},"filter":{"fundIds":[]},"targetCurrency":"RON","grouping":"FUND"}
        """.trimIndent()

        val request = Json.decodeFromString<MetricsReportRequestTO>(payload)

        assertThat(request.metrics).containsExactly(MetricTO.BALANCE, MetricTO.TOTAL_PROFIT)
        assertThat(request.interval.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(request.grouping).isEqualTo(GroupingCriteria.FUND)
    }
}
