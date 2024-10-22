package ro.jf.bk.fund.sdk
// TODO(Johann) change base package

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundName
import java.util.UUID.randomUUID

@ExtendWith(MockServerExtension::class)
class FundSdkTest {
    private val fundSdk = FundSdk(
        baseUrl = MockServerExtension.baseUrl,
        // TODO(Johann) extract to commons test, or have a default client in the SDK. might be even better
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    )

    @Test
    fun `test list funds`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val fundId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/fund/v1/funds")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("items", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(fundId.toString()))
                                    put("name", JsonPrimitive("Expenses"))
                                    put("accounts", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", JsonPrimitive(accountId.toString()))
                                        })
                                    })
                                })
                            })
                        }.toString()
                    )
            )

        val funds = fundSdk.listFunds(userId)

        assertThat(funds).hasSize(1)
        assertThat(funds[0].id).isEqualTo(fundId)
        assertThat(funds[0].name).isEqualTo(FundName("Expenses"))
    }

    @Test
    fun `test create fund`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val fundId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/fund/v1/funds")
                    .withHeader(Header(USER_ID_HEADER, userId.toString()))
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(fundId.toString()))
                            put("name", JsonPrimitive("Expenses"))
                            put("accounts", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(accountId.toString()))
                                })
                            })
                        }.toString()
                    )
            )

        val fund = fundSdk.createFund(userId, CreateFundTO(name = FundName("Expenses")))

        assertThat(fund.id).isEqualTo(fundId)
        assertThat(fund.name).isEqualTo(FundName("Expenses"))
    }
}
