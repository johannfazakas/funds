package ro.jf.funds.commons.test.extension

import mu.KotlinLogging.logger
import org.junit.jupiter.api.extension.*
import org.mockserver.client.MockServerClient
import org.testcontainers.containers.MockServerContainer
import org.testcontainers.utility.DockerImageName

private val log = logger { }


object MockServerContainerExtension : BeforeAllCallback, AfterEachCallback, ParameterResolver {
    val client: MockServerClient
        get() = runningContainer.run { MockServerClient(host, serverPort) }
    val baseUrl: String
        get() = runningContainer.run { "http://$host:$serverPort" }

    private val container: MockServerContainer =
        DockerImageName.parse("mockserver/mockserver")
            .withTag("mockserver-${MockServerClient::class.java.getPackage().implementationVersion}")
            .let(::MockServerContainer)
            .apply { withReuse(true) }

    private val runningContainer: MockServerContainer
        get() = container.also { ensureMockServerRunning() }

    override fun beforeAll(extensionContext: ExtensionContext) {
        ensureMockServerRunning()
    }

    override fun afterEach(context: ExtensionContext?) {
        resetMockServer()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.getType() in listOf(MockServerClient::class.java)

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        when (parameterContext.parameter.type) {
            MockServerClient::class.java -> client
            else -> throw IllegalArgumentException("Parameter couldn't be resolved ${parameterContext.parameter.name}.")
        }

    private fun ensureMockServerRunning() {
        container
            .takeIf { !it.isRunning }
            ?.apply {
                start()
                log.info("Started mock server @$baseUrl")
            }
    }

    private fun resetMockServer() {
        log.info("Resetting mock server @$baseUrl")
        client.reset()
    }
}
