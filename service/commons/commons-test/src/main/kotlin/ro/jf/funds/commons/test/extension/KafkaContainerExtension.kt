package ro.jf.funds.commons.test.extension

import mu.KotlinLogging.logger
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

private val log = logger { }

object KafkaContainerExtension : BeforeAllCallback {
    private val dockerImage = DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    private val container = KafkaContainer(dockerImage)
        .withReuse(true)

    private val runningContainer: KafkaContainer
        get() = container.apply { ensureRunning() }

    val bootstrapServers = runningContainer.bootstrapServers

    override fun beforeAll(context: ExtensionContext) {
        container.ensureRunning()
    }

    private fun KafkaContainer.ensureRunning() {
        if (!this.isRunning) {
            start()
            log.info { "Started kafka @${this.bootstrapServers}" }
        }
    }
}