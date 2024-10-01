package ro.jf.bk.user.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.ListTO
import ro.jf.bk.user.api.UserServiceApi
import ro.jf.bk.user.api.exception.UserApiException
import ro.jf.bk.user.api.model.CreateUserTO
import ro.jf.bk.user.api.model.UserTO
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5247"
private const val BASE_PATH = "/bk-api/user/v1"

private val log = logger { }

class UserServiceSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient,
) : UserServiceApi {
    override suspend fun listUsers(): List<UserTO> {
        val users = httpClient.get("$baseUrl$BASE_PATH/users").body<ListTO<UserTO>>()
        log.debug { "Retrieved users: $users" }
        return users.items
    }

    override suspend fun findUserById(userId: UUID): UserTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/users/$userId")
        if (response.status != HttpStatusCode.OK) {
            log.info { "User $userId not found by id" }
            return null
        }
        val user = response.body<UserTO>()
        log.debug { "Retrieved user: $user" }
        return user
    }

    override suspend fun findUserByUsername(username: String): UserTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/users/username/$username")
        if (response.status != HttpStatusCode.OK) {
            log.info { "User $username not found by username" }
            return null
        }
        val user = response.body<UserTO>()
        log.debug { "Retrieved user: $user" }
        return user
    }

    override suspend fun createUser(username: String): UserTO {
        val response = httpClient.post("$baseUrl$BASE_PATH/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserTO(username))
        }
        if (response.status == HttpStatusCode.Conflict) {
            log.info { "User $username already exists" }
            throw UserApiException.UsernameAlreadyExists(username)
        }
        val user = response.body<UserTO>()
        log.debug { "Created user: $user" }
        return user
    }

    override suspend fun deleteUserById(userId: UUID) {
        httpClient.delete("$baseUrl$BASE_PATH/users/$userId")
    }
}