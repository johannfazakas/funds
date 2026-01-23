package ro.jf.funds.reporting.api.serializer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.reporting.api.model.ReportDataIntervalTO
import ro.jf.funds.reporting.api.model.TimeGranularityTO
import ro.jf.funds.reporting.api.model.YearMonthTO


class ReportDataIntervalSerializerTest {

    @Test
    fun `should serialize monthly interval`() {
        val interval = ReportDataIntervalTO.Monthly(
            fromYearMonth = YearMonthTO(2021, 1),
            toYearMonth = YearMonthTO(2022, 12),
            forecastUntilYearMonth = YearMonthTO(2023, 12),
        ) as ReportDataIntervalTO

        val intervalJson = Json.encodeToJsonElement(interval)

        assertThat(intervalJson)
            .isEqualTo(buildJsonObject {
                put("type", JsonPrimitive(TimeGranularityTO.MONTHLY.name))
                put("fromYearMonth", JsonPrimitive("2021-01"))
                put("toYearMonth", JsonPrimitive("2022-12"))
                put("forecastUntilYearMonth", JsonPrimitive("2023-12"))
            })
    }

    @Test
    fun `should deserialize monthly interval`() {
        val intervalJson = buildJsonObject {
            put("type", JsonPrimitive(TimeGranularityTO.MONTHLY.name))
            put("fromYearMonth", JsonPrimitive("2021-01"))
            put("toYearMonth", JsonPrimitive("2022-12"))
            put("forecastUntilYearMonth", JsonPrimitive("2023-12"))
        }

        val interval = Json.decodeFromString<ReportDataIntervalTO>(intervalJson.toString())

        assertThat(interval.granularity).isEqualTo(TimeGranularityTO.MONTHLY)
        assertThat(interval.fromDate).isEqualTo(LocalDate(2021, 1, 1))
        assertThat(interval.toDate).isEqualTo(LocalDate(2022, 12, 31))
        assertThat(interval.forecastUntilDate).isEqualTo(LocalDate(2023, 12, 31))
    }

    @Test
    fun `should serialize daily interval`() {
        val interval = ReportDataIntervalTO.Daily(
            fromDate = LocalDate(2021, 1, 15),
            toDate = LocalDate(2021, 2, 14),
        ) as ReportDataIntervalTO

        val intervalJson = Json.encodeToJsonElement(interval)

        assertThat(intervalJson).isEqualTo(buildJsonObject {
            put("type", JsonPrimitive(TimeGranularityTO.DAILY.name))
            put("fromDate", JsonPrimitive("2021-01-15"))
            put("toDate", JsonPrimitive("2021-02-14"))
        })
    }

    @Test
    fun `should deserialize daily interval`() {
        val intervalJson = buildJsonObject {
            put("type", JsonPrimitive(TimeGranularityTO.DAILY.name))
            put("fromDate", JsonPrimitive("2021-01-15"))
            put("toDate", JsonPrimitive("2021-02-14"))
        }

        val interval = Json.decodeFromString<ReportDataIntervalTO>(intervalJson.toString())

        assertThat(interval.granularity).isEqualTo(TimeGranularityTO.DAILY)
        assertThat(interval.fromDate).isEqualTo(LocalDate(2021, 1, 15))
        assertThat(interval.toDate).isEqualTo(LocalDate(2021, 2, 14))
        assertThat(interval.forecastUntilDate).isEqualTo(null)
    }

    @Test
    fun `should serialize yearly interval`() {
        val interval = ReportDataIntervalTO.Yearly(
            fromYear = 2021,
            toYear = 2023,
            forecastUntilYear = 2025,
        ) as ReportDataIntervalTO

        val intervalJson = Json.encodeToJsonElement(interval)

        assertThat(intervalJson).isEqualTo(buildJsonObject {
            put("type", JsonPrimitive(TimeGranularityTO.YEARLY.name))
            put("fromYear", JsonPrimitive(2021))
            put("toYear", JsonPrimitive(2023))
            put("forecastUntilYear", JsonPrimitive(2025))
        })
    }

    @Test
    fun `should deserialize yearly interval`() {
        val intervalJson = buildJsonObject {
            put("type", JsonPrimitive(TimeGranularityTO.YEARLY.name))
            put("fromYear", JsonPrimitive(2021))
            put("toYear", JsonPrimitive(2023))
            put("forecastUntilYear", JsonPrimitive(2025))
        }

        val interval = Json.decodeFromString<ReportDataIntervalTO>(intervalJson.toString())

        assertThat(interval.granularity).isEqualTo(TimeGranularityTO.YEARLY)
        assertThat(interval.fromDate).isEqualTo(LocalDate(2021, 1, 1))
        assertThat(interval.toDate).isEqualTo(LocalDate(2023, 12, 31))
        assertThat(interval.forecastUntilDate).isEqualTo(LocalDate(2025, 12, 31))
    }
}