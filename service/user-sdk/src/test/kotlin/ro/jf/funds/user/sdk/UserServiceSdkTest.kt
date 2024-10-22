package ro.jf.funds.user.sdk

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.commons.test.extension.MockServerExtension
import ro.jf.funds.user.api.exception.UserApiException
import java.util.UUID.randomUUID
import kotlin.test.assertFailsWith


@ExtendWith(MockServerExtension::class)
class UserServiceSdkTest {
    private val userServiceSdk = UserServiceSdk(
        baseUrl = MockServerExtension.baseUrl,
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
    fun `test list users`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/user/v1/users")
            )
            .respond(
                response()
                    .withBody(
                        """
                        {
                            "items": [
                                {
                                    "id": "${randomUUID()}",
                                    "username": "Johann"
                                },
                                {
                                    "id": "${randomUUID()}",
                                    "username": "Olivia"
                                },
                                {
                                    "id": "${randomUUID()}",
                                    "username": "Crina"
                                }
                            ]
                        }
                    """.trimIndent()
                    )
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200)
            )

        val response = userServiceSdk.listUsers()

        assertThat(response).hasSize(3)
        assertThat(response.map { it.username }).containsExactlyInAnyOrder("Johann", "Olivia", "Crina")
    }

    @Test
    fun `test find user by id`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/user/v1/users/$userId")
            )
            .respond(
                response()
                    .withBody(
                        """
                        {
                            "id": "$userId",
                            "username": "Johann"
                        }
                    """.trimIndent()
                    )
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200)
            )

        val response = userServiceSdk.findUserById(userId)

        assertThat(response).isNotNull
        assertThat(response!!.username).isEqualTo("Johann")
        assertThat(response.id).isEqualTo(userId)
    }

    @Test
    fun `test find user by id when not found`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/user/v1/users/$userId")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(404)
            )

        val response = userServiceSdk.findUserById(userId)

        assertThat(response).isNull()
    }

    @Test
    fun `test find user by username`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val username = "username-$userId"
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/user/v1/users/username/$username")
            )
            .respond(
                response()
                    .withBody(
                        """
                        {
                            "id": "$userId",
                            "username": "$username"
                        }
                    """.trimIndent()
                    )
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200)
            )

        val response = userServiceSdk.findUserByUsername(username)

        assertThat(response).isNotNull
        assertThat(response!!.username).isEqualTo(username)
        assertThat(response.id).isEqualTo(userId)
    }

    @Test
    fun `test find user by username when not found`(mockServerClient: MockServerClient): Unit = runBlocking {
        val username = "username-${randomUUID()}"
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/bk-api/user/v1/users/$username")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(404)
            )

        val response = userServiceSdk.findUserByUsername(username)

        assertThat(response).isNull()
    }

    @Test
    fun `test create user`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val username = "username-$userId"
        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/user/v1/users")
            )
            .respond(
                response()
                    .withBody(
                        """
                        {
                            "id": "$userId",
                            "username": "$username"
                        }
                    """.trimIndent()
                    )
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(201)
            )

        val response = userServiceSdk.createUser(username)

        assertThat(response).isNotNull
        assertThat(response.username).isEqualTo(username)
        assertThat(response.id).isEqualTo(userId)
    }

    @Test
    fun `test create user when username exists`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val username = "username-$userId"
        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/user/v1/users")
                    .withBody(
                        """
                        {
                            "username": "$username"
                        }
                    """.trimIndent()
                    )
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(409)
            )

        assertFailsWith<UserApiException.UsernameAlreadyExists>("User with username $username already exists.") {
            userServiceSdk.createUser(username)
        }
    }

    @Test
    fun `test delete user`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/bk-api/user/v1/users/$userId")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(204)
            )

        userServiceSdk.deleteUserById(userId)
    }
}
