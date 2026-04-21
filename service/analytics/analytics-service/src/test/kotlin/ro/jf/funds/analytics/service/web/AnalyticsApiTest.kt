package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import ro.jf.funds.analytics.api.model.AnalyticsReportTO
import ro.jf.funds.analytics.api.model.AnalyticsReportRequestTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.*
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import io.ktor.server.config.*
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AnalyticsApiTest {
    private val database = PostgresContainerExtension.connection
    private val analyticsRecordRepository = AnalyticsRecordRepository(database)

    private val userId = uuid4()
    private val accountId = uuid4()
    private val fundId = uuid4()
    private val otherFundId = uuid4()

    private val conversionServiceConfig = MapApplicationConfig(
        "integration.conversion-service.base-url" to "http://localhost:0",
    )

    @Test
    fun `given analytics records across months - when requesting balance report - then returns cumulative balance with gap filling and fund filtering`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)

            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-15T10:00:00"), amount = "500.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-20T10:00:00"), amount = "-30.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-15T10:00:00"), amount = "50.00", fundId = otherFundId),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-03-15T10:00:00"), amount = "-50.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-03-20T10:00:00"), amount = "200.00"),
                )
            )

            val client = createJsonHttpClient()

            val unfilteredResponse = client.post("/funds-api/analytics/v1/reports/balance") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    AnalyticsReportRequestTO(
                        granularity = TimeGranularity.MONTHLY,
                        from = LocalDateTime.parse("2024-01-01T00:00:00"),
                        to = LocalDateTime.parse("2024-04-01T00:00:00"),
                        targetCurrency = Currency.RON,
                    )
                )
            }

            assertThat(unfilteredResponse.status).isEqualTo(HttpStatusCode.OK)
            val unfilteredReport = unfilteredResponse.body<AnalyticsReportTO>()
            assertThat(unfilteredReport.granularity).isEqualTo(TimeGranularity.MONTHLY)
            assertThat(unfilteredReport.buckets).hasSize(3)

            assertThat(unfilteredReport.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
            assertThat(unfilteredReport.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("500.00"))

            assertThat(unfilteredReport.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
            assertThat(unfilteredReport.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("620.00"))

            assertThat(unfilteredReport.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
            assertThat(unfilteredReport.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("620.00"))

            val filteredResponse = client.post("/funds-api/analytics/v1/reports/balance") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    AnalyticsReportRequestTO(
                        granularity = TimeGranularity.MONTHLY,
                        from = LocalDateTime.parse("2024-01-01T00:00:00"),
                        to = LocalDateTime.parse("2024-04-01T00:00:00"),
                        fundIds = listOf(fundId),
                        targetCurrency = Currency.RON,
                    )
                )
            }

            assertThat(filteredResponse.status).isEqualTo(HttpStatusCode.OK)
            val filteredReport = filteredResponse.body<AnalyticsReportTO>()
            assertThat(filteredReport.buckets).hasSize(3)

            assertThat(filteredReport.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
            assertThat(filteredReport.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("500.00"))

            assertThat(filteredReport.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
            assertThat(filteredReport.buckets[1].groups[0].value).isEqualTo(BigDecimal.parseString("570.00"))

            assertThat(filteredReport.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
            assertThat(filteredReport.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("570.00"))
        }

    @Test
    fun `given analytics records across months - when requesting net change report - then returns per-bucket net changes`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)

            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-15T10:00:00"), amount = "500.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-20T10:00:00"), amount = "-30.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-15T10:00:00"), amount = "50.00", fundId = otherFundId),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-03-15T10:00:00"), amount = "-50.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-03-20T10:00:00"), amount = "200.00"),
                )
            )

            val client = createJsonHttpClient()

            val response = client.post("/funds-api/analytics/v1/reports/net-change") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    AnalyticsReportRequestTO(
                        granularity = TimeGranularity.MONTHLY,
                        from = LocalDateTime.parse("2024-01-01T00:00:00"),
                        to = LocalDateTime.parse("2024-04-01T00:00:00"),
                        targetCurrency = Currency.RON,
                    )
                )
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val report = response.body<AnalyticsReportTO>()
            assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
            assertThat(report.buckets).hasSize(3)

            assertThat(report.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
            assertThat(report.buckets[0].groups[0].value).isEqualTo(BigDecimal.parseString("120.00"))

            assertThat(report.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
            assertThat(report.buckets[1].groups[0].value).isEqualTo(BigDecimal.ZERO)

            assertThat(report.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
            assertThat(report.buckets[2].groups[0].value).isEqualTo(BigDecimal.parseString("150.00"))
        }

    private fun analyticsRecord(
        dateTime: LocalDateTime,
        amount: String,
        fundId: com.benasher44.uuid.Uuid = this.fundId,
    ) = AnalyticsRecord(
        id = uuid4(),
        userId = userId,
        fundId = fundId,
        accountId = accountId,
        transactionId = uuid4(),
        transactionType = TransactionType.SINGLE_RECORD,
        dateTime = dateTime,
        amount = BigDecimal.parseString(amount),
        unit = Currency("RON"),
        category = null,
    )

    private fun Application.testModule() {
        configureDependencies(analyticsDependencies)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureAnalyticsRouting()
    }
}
