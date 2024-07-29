package ro.jf.bk.fund.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.commons.test.extension.PostgresContainerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.service.adapter.persistence.FundExposedRepository
import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class FundApiTest {

    private val fundRepository = createFundRepository()

    @Test
    fun `test list funds`() = testApplication {
        configureEnvironment()

        val userId = randomUUID()
        val fund = fundRepository.save(CreateFundCommand(userId, "Expenses"))

        val response = createJsonHttpClient().get("/bk-api/fund/v1/funds") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val funds = response.body<ListTO<FundTO>>()
        assertThat(funds.items).hasSize(1)
        assertThat(funds.items.first().name).isEqualTo("Expenses")
        assertThat(funds.items.first().id).isEqualTo(fund.id)
    }

    @Test
    fun `test create fund`() = testApplication {
        configureEnvironment()

        val userId = randomUUID()
        val response = createJsonHttpClient().post("/bk-api/fund/v1/funds") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(CreateFundTO("Investment Portfolio"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val fundTO = response.body<FundTO>()
        assertThat(fundTO).isNotNull
        assertThat(fundTO.name).isEqualTo("Investment Portfolio")

        val dbFund = fundRepository.findById(userId, fundTO.id)
        assertThat(dbFund).isNotNull
        assertThat(dbFund!!.name).isEqualTo("Investment Portfolio")
        assertThat(dbFund.userId).isEqualTo(userId)
    }

    @Test
    fun `test delete fund by id`() = testApplication {
        configureEnvironment()
        val userId = randomUUID()
        val fund = fundRepository.save(CreateFundCommand(userId, "Company"))

        val response = createJsonHttpClient().delete("/bk-api/fund/v1/funds/${fund.id}") {
            header(USER_ID_HEADER, userId)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(fundRepository.findById(userId, fund.id)).isNull()
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private fun ApplicationTestBuilder.configureEnvironment() {
        environment {
            config = MapApplicationConfig(
                "database.url" to PostgresContainerExtension.jdbcUrl,
                "database.user" to PostgresContainerExtension.username,
                "database.password" to PostgresContainerExtension.password
            )
        }
        application {
            module()
        }
    }

    private fun createFundRepository() = FundExposedRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}
