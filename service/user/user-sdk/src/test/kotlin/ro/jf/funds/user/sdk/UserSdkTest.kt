package ro.jf.funds.user.sdk

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.user.api.exception.UserApiException
import java.util.UUID.randomUUID
import kotlin.test.assertFailsWith


@ExtendWith(MockServerContainerExtension::class)
class UserSdkTest {
    private val userSdk = UserSdk(
        baseUrl = MockServerContainerExtension.baseUrl
    )

    @Test
    fun `test list users`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/user/v1/users")
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

        val response = userSdk.listUsers()

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
                    .withPath("/funds-api/user/v1/users/$userId")
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

        val response = userSdk.findUserById(userId)

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
                    .withPath("/funds-api/user/v1/users/$userId")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(404)
            )

        val response = userSdk.findUserById(userId)

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
                    .withPath("/funds-api/user/v1/users/username/$username")
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

        val response = userSdk.findUserByUsername(username)

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
                    .withPath("/funds-api/user/v1/users/$username")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(404)
            )

        val response = userSdk.findUserByUsername(username)

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
                    .withPath("/funds-api/user/v1/users")
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

        val response = userSdk.createUser(username)

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
                    .withPath("/funds-api/user/v1/users")
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
            userSdk.createUser(username)
        }
    }

    @Test
    fun `test delete user`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        mockServerClient
            .`when`(
                request()
                    .withMethod("DELETE")
                    .withPath("/funds-api/user/v1/users/$userId")
            )
            .respond(
                response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(204)
            )

        userSdk.deleteUserById(userId)
    }
}
