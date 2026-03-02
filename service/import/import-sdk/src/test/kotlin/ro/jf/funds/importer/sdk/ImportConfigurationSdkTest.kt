package ro.jf.funds.importer.sdk

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER

@ExtendWith(MockServerContainerExtension::class)
class ImportConfigurationSdkTest {
    private val importConfigurationSdk = ImportConfigurationSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `given list import configurations request should return page`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val configId = uuid4()
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/import/v1/import-configurations")
                    .withHeader(USER_ID_HEADER, userId.toString())
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("items", buildJsonArray {
                                add(buildJsonObject {
                                    put("importConfigurationId", JsonPrimitive(configId.toString()))
                                    put("name", JsonPrimitive("test-config"))
                                    put("accountMatchers", buildJsonArray {})
                                    put("fundMatchers", buildJsonArray {})
                                    put("createdAt", JsonPrimitive("2026-01-01T00:00:00"))
                                })
                            })
                            put("total", JsonPrimitive(1))
                        }.toString()
                    )
            )

        val response = importConfigurationSdk.listImportConfigurations(userId)

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].name).isEqualTo("test-config")
    }
}
