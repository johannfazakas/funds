package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.client.api.model.UserTO

private const val LOCALHOST_BASE_URL = "http://localhost:5247"
private const val BASE_PATH = "/funds-api/user/v1"

class UserSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("UserSdk")

    suspend fun findUserByUsername(username: String): UserTO? {
        val response = httpClient.get("$baseUrl$BASE_PATH/users/username/$username")
        if (response.status != HttpStatusCode.OK) {
            log.i { "User $username not found by username" }
            return null
        }
        val user = response.body<UserTO>()
        log.d { "Retrieved user: $user" }
        return user
    }
}
