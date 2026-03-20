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
import ro.jf.funds.analytics.api.model.AnalyticsValueReportRequestTO
import ro.jf.funds.analytics.api.model.AnalyticsValueReportTO
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
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.createJsonHttpClient
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
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

    @Test
    fun `given analytics records across months - when requesting value report - then returns cumulative balance with gap filling and fund filtering`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

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

            val unfilteredResponse = client.post("/funds-api/analytics/v1/records/value-report") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    AnalyticsValueReportRequestTO(
                        granularity = TimeGranularity.MONTHLY,
                        from = LocalDateTime.parse("2024-01-01T00:00:00"),
                        to = LocalDateTime.parse("2024-04-01T00:00:00"),
                    )
                )
            }

            assertThat(unfilteredResponse.status).isEqualTo(HttpStatusCode.OK)
            val unfilteredReport = unfilteredResponse.body<AnalyticsValueReportTO>()
            assertThat(unfilteredReport.granularity).isEqualTo(TimeGranularity.MONTHLY)
            assertThat(unfilteredReport.buckets).hasSize(3)

            assertThat(unfilteredReport.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
            assertThat(unfilteredReport.buckets[0].netChange).isEqualTo(BigDecimal.parseString("120.00"))
            assertThat(unfilteredReport.buckets[0].balance).isEqualTo(BigDecimal.parseString("620.00"))

            assertThat(unfilteredReport.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
            assertThat(unfilteredReport.buckets[1].netChange).isEqualTo(BigDecimal.ZERO)
            assertThat(unfilteredReport.buckets[1].balance).isEqualTo(BigDecimal.parseString("620.00"))

            assertThat(unfilteredReport.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
            assertThat(unfilteredReport.buckets[2].netChange).isEqualTo(BigDecimal.parseString("150.00"))
            assertThat(unfilteredReport.buckets[2].balance).isEqualTo(BigDecimal.parseString("770.00"))

            val filteredResponse = client.post("/funds-api/analytics/v1/records/value-report") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    AnalyticsValueReportRequestTO(
                        granularity = TimeGranularity.MONTHLY,
                        from = LocalDateTime.parse("2024-01-01T00:00:00"),
                        to = LocalDateTime.parse("2024-04-01T00:00:00"),
                        fundIds = listOf(fundId),
                    )
                )
            }

            assertThat(filteredResponse.status).isEqualTo(HttpStatusCode.OK)
            val filteredReport = filteredResponse.body<AnalyticsValueReportTO>()
            assertThat(filteredReport.buckets).hasSize(3)

            assertThat(filteredReport.buckets[0].dateTime).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"))
            assertThat(filteredReport.buckets[0].netChange).isEqualTo(BigDecimal.parseString("70.00"))
            assertThat(filteredReport.buckets[0].balance).isEqualTo(BigDecimal.parseString("570.00"))

            assertThat(filteredReport.buckets[1].dateTime).isEqualTo(LocalDateTime.parse("2024-02-01T00:00:00"))
            assertThat(filteredReport.buckets[1].netChange).isEqualTo(BigDecimal.ZERO)
            assertThat(filteredReport.buckets[1].balance).isEqualTo(BigDecimal.parseString("570.00"))

            assertThat(filteredReport.buckets[2].dateTime).isEqualTo(LocalDateTime.parse("2024-03-01T00:00:00"))
            assertThat(filteredReport.buckets[2].netChange).isEqualTo(BigDecimal.parseString("150.00"))
            assertThat(filteredReport.buckets[2].balance).isEqualTo(BigDecimal.parseString("720.00"))
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
        labels = emptyList(),
    )

    private fun Application.testModule() {
        configureDependencies(analyticsDependencies)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureAnalyticsRouting()
    }
}
