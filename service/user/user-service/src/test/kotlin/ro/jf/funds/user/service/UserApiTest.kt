package ro.jf.funds.user.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.service.adapter.persistence.UserExposedRepository
import ro.jf.funds.user.service.config.configureUserErrorHandling
import ro.jf.funds.user.service.config.configureUserRouting
import ro.jf.funds.user.service.config.userDependencies
import ro.jf.funds.user.service.domain.command.CreateUserCommand
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class UserApiTest {

    private val userRepository = UserExposedRepository(PostgresContainerExtension.connection)

    @AfterEach
    fun tearDown() = runBlocking {
        userRepository.deleteAll()
    }

    @Test
    fun `test list users`() = testApplication {
        configureEnvironment()

        userRepository.save(CreateUserCommand("Olivia"))
        userRepository.save(CreateUserCommand("Crina"))
        userRepository.save(CreateUserCommand("Johann"))

        val response = createJsonHttpClient().get("/funds-api/user/v1/users")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val users = response.body<ListTO<UserTO>>()
        assertThat(users.items).hasSize(3)
        assertThat(users.items.map { it.username }).containsExactlyInAnyOrder("Olivia", "Crina", "Johann")
    }

    @Test
    fun `test get user by id`() = testApplication {
        configureEnvironment()

        val user = userRepository.save(CreateUserCommand("Johann"))

        val response = createJsonHttpClient().get("/funds-api/user/v1/users/${user.id}")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val userTO = response.body<UserTO>()
        assertThat(userTO).isNotNull
        assertThat(userTO.username).isEqualTo("Johann")
    }

    @Test
    fun `given missing user - when get user by id - then returns 404 with error body`() = testApplication {
        configureEnvironment()
        val userId = randomUUID()

        val response = createJsonHttpClient().get("/funds-api/user/v1/users/$userId")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        val error = response.body<ErrorTO>()
        assertThat(error.title).isEqualTo("User not found")
        assertThat(error.detail).isEqualTo("User with id '$userId' not found")
    }

    @Test
    fun `test get user by username`() = testApplication {
        configureEnvironment()
        val username = "Johann"
        userRepository.save(CreateUserCommand(username))

        val response = createJsonHttpClient().get("/funds-api/user/v1/users/username/$username")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val userTO = response.body<UserTO>()
        assertThat(userTO).isNotNull
        assertThat(userTO.username).isEqualTo(username)
    }

    @Test
    fun `test create user`() = testApplication {
        configureEnvironment()

        val response = createJsonHttpClient().post("/funds-api/user/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserTO("username"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val userTO = response.body<UserTO>()
        assertThat(userTO).isNotNull
        assertThat(userTO.username).isEqualTo("username")

        val dbUser = userRepository.findById(userTO.id)
        assertThat(dbUser).isNotNull
        assertThat(dbUser!!.username).isEqualTo("username")
    }

    @Test
    fun `test create user with duplicate username`() = testApplication {
        configureEnvironment()

        userRepository.save(CreateUserCommand("username"))

        val response = createJsonHttpClient().post("/funds-api/user/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserTO("username"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun `test delete user by id`() = testApplication {
        configureEnvironment()

        val user = userRepository.save(CreateUserCommand("username"))

        val response = createJsonHttpClient().delete("/funds-api/user/v1/users/${user.id}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(userRepository.findById(user.id)).isNull()
    }

    @Test
    fun `test delete not existing user by id`() = testApplication {
        configureEnvironment()

        val response = createJsonHttpClient().delete("/funds-api/user/v1/users/${randomUUID()}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json() } }

    private fun ApplicationTestBuilder.configureEnvironment() {
        application {
            configureDependencies(userDependencies)
            configureContentNegotiation()
            configureUserErrorHandling()
            configureDatabaseMigration(get<DataSource>())
            configureUserRouting()
        }
        environment {
            config = dbConfig
        }
    }
}
