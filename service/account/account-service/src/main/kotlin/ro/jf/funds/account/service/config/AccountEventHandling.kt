package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging.logger
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.koin.ktor.ext.inject
import java.time.Duration

private val logger = logger {}

fun Application.configureAccountEventHandling() {
    val consumer by inject<KafkaConsumer<String, String>>()
    val producer by inject<KafkaProducer<String, String>>()

    logger.info { "Configuring account event handling" }
    CoroutineScope(Dispatchers.IO).launch {
        // TODO(Johann) the topic name or pattern should be extracted somewhere
        consumer.subscribe(listOf("local.funds.account.transactions-request"))
        logger.info { "Subscribed to local.funds.account.transactions-request" }
        while (isActive) {
            logger.info { "Polling for records" }
            val records = consumer.poll(Duration.ofMillis(500))
            logger.info { "Received ${records.count()} records" }
            records.forEach { record ->
                // Handle your message here
                logger.info { "Received record $record" }
                // TODO(Johann) use another response model
                producer.send(ProducerRecord("local.funds.account.transactions-response", record.key(), "OK"))
            }
        }
    }
}
