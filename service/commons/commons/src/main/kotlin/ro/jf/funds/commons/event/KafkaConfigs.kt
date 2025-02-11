package ro.jf.funds.commons.event

import io.ktor.server.application.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import ro.jf.funds.commons.config.getStringProperty
import java.util.*

private const val KAFKA_BOOTSTRAP_SERVERS_PROPERTY = "kafka.bootstrap-servers"
private const val KAFKA_CLIENT_ID_PROPERTY = "kafka.client-id"
private const val KAFKA_GROUP_ID_PROPERTY = "kafka.group-id"

fun createKafkaProducer(properties: ProducerProperties): KafkaProducer<String, String> {
    return KafkaProducer(Properties().also {
        it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = properties.bootstrapServers
        it[ProducerConfig.CLIENT_ID_CONFIG] = properties.clientId
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.RETRIES_CONFIG] = 3
        it[ProducerConfig.ACKS_CONFIG] = "all"
    })
}

data class ProducerProperties(
    val bootstrapServers: String,
    val clientId: String
) {
    companion object {
        fun fromEnv(config: ApplicationEnvironment): ProducerProperties {
            return ProducerProperties(
                bootstrapServers = config.getStringProperty(KAFKA_BOOTSTRAP_SERVERS_PROPERTY),
                clientId = config.getStringProperty(KAFKA_CLIENT_ID_PROPERTY)
            )
        }
    }
}


data class ConsumerProperties(
    val bootstrapServers: String,
    val groupId: String
) {
    companion object {
        fun fromEnv(config: ApplicationEnvironment): ConsumerProperties {
            return ConsumerProperties(
                bootstrapServers = config.getStringProperty(KAFKA_BOOTSTRAP_SERVERS_PROPERTY),
                groupId = config.getStringProperty(KAFKA_GROUP_ID_PROPERTY)
            )
        }
    }
}
