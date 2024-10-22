package ro.jf.funds.user.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.service.adapter.persistence.UserExposedRepository
import ro.jf.funds.user.service.domain.command.CreateUserCommand
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class UserApiTest {

    private val userRepository = createUserRepository()

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

        val response = createJsonHttpClient().get("/bk-api/user/v1/users")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val users = response.body<ro.jf.funds.commons.model.ListTO<UserTO>>()
        assertThat(users.items).hasSize(3)
        assertThat(users.items.map { it.username }).containsExactlyInAnyOrder("Olivia", "Crina", "Johann")
    }

    @Test
    fun `test get user by id`() = testApplication {
        configureEnvironment()

        val user = userRepository.save(CreateUserCommand("Johann"))

        val response = createJsonHttpClient().get("/bk-api/user/v1/users/${user.id}")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val userTO = response.body<UserTO>()
        assertThat(userTO).isNotNull
        assertThat(userTO.username).isEqualTo("Johann")
    }

    @Test
    fun `test get user by id when missing`() = testApplication {
        configureEnvironment()

        val response = createJsonHttpClient().get("/bk-api/user/v1/users/${randomUUID()}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `test get user by username`() = testApplication {
        configureEnvironment()
        val username = "Johann"
        userRepository.save(CreateUserCommand(username))

        val response = createJsonHttpClient().get("/bk-api/user/v1/users/username/$username")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val userTO = response.body<UserTO>()
        assertThat(userTO).isNotNull
        assertThat(userTO.username).isEqualTo(username)
    }

    @Test
    fun `test create user`() = testApplication {
        configureEnvironment()

        val response = createJsonHttpClient().post("/bk-api/user/v1/users") {
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

        val response = createJsonHttpClient().post("/bk-api/user/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserTO("username"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun `test delete user by id`() = testApplication {
        configureEnvironment()
        val user = userRepository.save(CreateUserCommand("username"))

        val response = createJsonHttpClient().delete("/bk-api/user/v1/users/${user.id}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(userRepository.findById(user.id)).isNull()
    }

    @Test
    fun `test delete not existing user by id`() = testApplication {
        configureEnvironment()

        val response = createJsonHttpClient().delete("/bk-api/user/v1/users/${randomUUID()}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json() } }

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

    private fun createUserRepository() = UserExposedRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}
