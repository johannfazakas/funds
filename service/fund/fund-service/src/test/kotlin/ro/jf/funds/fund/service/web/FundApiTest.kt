package ro.jf.funds.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import ro.jf.funds.platform.api.model.ListTO
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
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.service.config.configureFundErrorHandling
import ro.jf.funds.fund.service.config.configureFundRouting
import ro.jf.funds.fund.service.config.fundDependencies
import ro.jf.funds.fund.service.persistence.FundRepository
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class FundApiTest {
    private val fundRepository = createFundRepository()

    @Test
    fun `test list funds`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val fund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/funds") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val funds = response.body<ListTO<FundTO>>()
        assertThat(funds.items).hasSize(1)
        assertThat(funds.items.first().name).isEqualTo(FundName("Expenses"))
        assertThat(funds.items.first().id).isEqualTo(fund.id)
    }

    @Test
    fun `test get fund by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()
        val fund = fundRepository.save(userId, CreateFundTO(FundName("Savings")))

        val response = createJsonHttpClient().get("/funds-api/fund/v1/funds/${fund.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val fundTO = response.body<FundTO>()
        assertThat(fundTO).isNotNull
        assertThat(fundTO.name).isEqualTo(FundName("Savings"))
        assertThat(fundTO.id).isEqualTo(fund.id)
    }

    @Test
    fun `test create fund`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val userId = randomUUID()

        val response = createJsonHttpClient().post("/funds-api/fund/v1/funds") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateFundTO(FundName("Investment Portfolio")))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val fundTO = response.body<FundTO>()
        assertThat(fundTO).isNotNull
        assertThat(fundTO.name).isEqualTo(FundName("Investment Portfolio"))

        val dbFund = fundRepository.findById(userId, fundTO.id)
        assertThat(dbFund).isNotNull
        assertThat(dbFund!!.name).isEqualTo(FundName("Investment Portfolio"))
        assertThat(dbFund.userId).isEqualTo(userId)
    }

    @Test
    fun `test delete fund by id`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val userId = randomUUID()
        val fund = fundRepository.save(userId, CreateFundTO(FundName("Company")))

        val response = createJsonHttpClient().delete("/funds-api/fund/v1/funds/${fund.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(fundRepository.findById(userId, fund.id)).isNull()
    }

    private fun Application.testModule() {
        configureDependencies(fundDependencies)
        configureFundErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureFundRouting()
    }

    private fun createFundRepository() = FundRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}
