package ro.jf.funds.fund.sdk

import kotlinx.coroutines.runBlocking
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
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class FundSdkTest {
    private val fundSdk = FundSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `test list funds`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val fundId = randomUUID()
        val accountId = randomUUID()

        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/fund/v1/funds")
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

        assertThat(funds.items).hasSize(1)
        assertThat(funds.items[0].id).isEqualTo(fundId)
        assertThat(funds.items[0].name).isEqualTo(FundName("Expenses"))
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
                    .withPath("/funds-api/fund/v1/funds")
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
