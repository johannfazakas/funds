package ro.jf.bk.fund.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.service.config.configureContentNegotiation
import ro.jf.bk.commons.service.config.configureDatabaseMigration
import ro.jf.bk.commons.service.config.configureDependencies
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.test.extension.PostgresContainerExtension
import ro.jf.bk.commons.test.utils.configureEnvironmentWithDB
import ro.jf.bk.commons.test.utils.createJsonHttpClient
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.CreateFundAccountTO
import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.config.configureRouting
import ro.jf.bk.fund.service.config.fundsAppModule
import ro.jf.bk.fund.service.persistence.FundRepository
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(MockServerExtension::class)
class FundApiTest {

    private val fundRepository = createFundRepository()

    @Test
    fun `test list funds`() = testApplication {
        configureEnvironmentWithDB(appConfig) { testModule() }

        val userId = randomUUID()
        val accountId = randomUUID()
        val fund = fundRepository.save(
            userId,
            CreateFundTO(
                FundName("Expenses"),
                listOf(
                    CreateFundAccountTO(accountId)
                )
            )
        )

        val response = createJsonHttpClient().get("/bk-api/fund/v1/funds") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val funds = response.body<ListTO<FundTO>>()
        assertThat(funds.items).hasSize(1)
        assertThat(funds.items.first().name).isEqualTo(FundName("Expenses"))
        assertThat(funds.items.first().id).isEqualTo(fund.id)
        assertThat(funds.items.first().accounts).hasSize(1)
        assertThat(funds.items.first().accounts.first().id).isEqualTo(accountId)
    }

    @Test
    fun `test get fund by id`() = testApplication {
        configureEnvironmentWithDB(appConfig) { testModule() }

        val userId = randomUUID()
        val accountId = randomUUID()
        val fund = fundRepository.save(
            userId,
            CreateFundTO(
                FundName("Savings"),
                listOf(
                    CreateFundAccountTO(accountId)
                )
            )
        )

        val response = createJsonHttpClient().get("/bk-api/fund/v1/funds/${fund.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val fundTO = response.body<FundTO>()
        assertThat(fundTO).isNotNull
        assertThat(fundTO.name).isEqualTo(FundName("Savings"))
        assertThat(fundTO.id).isEqualTo(fund.id)
        assertThat(fundTO.accounts).hasSize(1)
        assertThat(fundTO.accounts.first().id).isEqualTo(accountId)
    }

    @Test
    fun `test create fund`(mockServerClient: MockServerClient) = testApplication {
        configureEnvironmentWithDB(appConfig) { testModule() }

        val userId = randomUUID()
        val accountId = randomUUID()

        // TODO(Johann) should this be tested here? it is already tested in sdk
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/account/v1/accounts/$accountId")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        """
                        {
                            "id": "$accountId",
                            "name": "Savings Account",
                            "type": "currency",
                            "currency": "RON"   
                        }
                        """.trimIndent()
                    )
            )

        val response = createJsonHttpClient().post("/bk-api/fund/v1/funds") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(
                CreateFundTO(
                    FundName("Investment Portfolio"),
                    listOf(
                        CreateFundAccountTO(accountId)
                    )
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val fundTO = response.body<FundTO>()
        assertThat(fundTO).isNotNull
        assertThat(fundTO.name).isEqualTo(FundName("Investment Portfolio"))
        assertThat(fundTO.accounts).hasSize(1)
        assertThat(fundTO.accounts.first().id).isEqualTo(accountId)

        val dbFund = fundRepository.findById(userId, fundTO.id)
        assertThat(dbFund).isNotNull
        assertThat(dbFund!!.name).isEqualTo(FundName("Investment Portfolio"))
        assertThat(dbFund.userId).isEqualTo(userId)
        assertThat(dbFund.accounts).hasSize(1)
        assertThat(dbFund.accounts.first().id).isEqualTo(accountId)
    }

    @Test
    fun `test delete fund by id`() = testApplication {
        configureEnvironmentWithDB(appConfig) { testModule() }
        val userId = randomUUID()
        val fund = fundRepository.save(userId, CreateFundTO(FundName("Company"), listOf()))

        val response = createJsonHttpClient().delete("/bk-api/fund/v1/funds/${fund.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(fundRepository.findById(userId, fund.id)).isNull()
    }

    private val appConfig = MapApplicationConfig(
        "integration.account-service.base-url" to MockServerExtension.baseUrl
    )

    private fun Application.testModule() {
        configureDependencies(fundsAppModule)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureRouting()
    }

    private fun createFundRepository() = FundRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}
