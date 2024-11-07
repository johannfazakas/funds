package ro.jf.funds.commons.test.extension

import mu.KotlinLogging.logger
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.RemoveMembersFromConsumerGroupOptions
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.*

private val log = logger { }

object KafkaContainerExtension : BeforeAllCallback, AfterAllCallback {
    private val dockerImage = DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    private val container = KafkaContainer(dockerImage)
        .withReuse(true)

    private val runningContainer: KafkaContainer by lazy { container.apply { ensureRunning() } }

    val bootstrapServers: String by lazy { runningContainer.bootstrapServers }

    val adminClient: AdminClient by lazy {
        AdminClient.create(Properties().also {
            it["bootstrap.servers"] = bootstrapServers
        })
    }

    override fun beforeAll(context: ExtensionContext) {
        container.ensureRunning()
    }

    override fun afterAll(context: ExtensionContext) {
        container.reset()
    }

    private fun KafkaContainer.ensureRunning() {
        if (!this.isRunning) {
            start()
            log.info { "Started kafka @${this.bootstrapServers}" }
        }
    }

    private fun KafkaContainer.reset() {
        log.info { "Resetting kafka @${this.bootstrapServers}" }
        adminClient.listConsumerGroups().all().get().map { it.groupId() }.let { consumerGroupIds ->
            adminClient.describeConsumerGroups(consumerGroupIds).all().get().let { consumerGroupsById ->
                consumerGroupsById
                    .filter { it.value.members().isNotEmpty() }
                    .forEach {
                        adminClient.removeMembersFromConsumerGroup(it.key, RemoveMembersFromConsumerGroupOptions()).all().get()
                    }
            }
            adminClient.deleteConsumerGroups(consumerGroupIds).all().get()
        }
        adminClient.listTopics().names().get().let { topics ->
            adminClient.deleteTopics(topics).all().get()
        }
    }
}
