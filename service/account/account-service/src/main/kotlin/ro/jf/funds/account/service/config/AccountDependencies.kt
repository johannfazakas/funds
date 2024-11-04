package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.account.service.service.AccountService
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.service.environment.getStringProperty
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

val Application.accountDependencies
    get() = module {
        single<DataSource> {
            PGSimpleDataSource().apply {
                setURL(environment.config.property("database.url").getString())
                user = environment.config.property("database.user").getString()
                password = environment.config.property("database.password").getString()
            }
        }
        single<Database> { Database.connect(datasource = get()) }
        single {
            // TODO(Johann) could extract some common thing
            DriverManager.getConnection(
                environment.config.property("database.url").getString(),
                environment.config.property("database.user").getString(),
                environment.config.property("database.password").getString()
            )
        }
        single<KafkaConsumer<String, String>> {
            // TODO(Johann) could extract some common thing from here
            KafkaConsumer(Properties().also {
                it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] =
                    environment.config.property("kafka.bootstrapServers").getString()
                it[ConsumerConfig.GROUP_ID_CONFIG] = environment.config.property("kafka.groupId").getString()
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            })
        }
        single<KafkaProducer<String, String>> {
            // TODO(Johann) could extract some common thing from here
            // TODO(Johann) could have generic producer
            KafkaProducer(Properties().also {
                it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] =
                    environment.getStringProperty("kafka.bootstrapServers")
                it[ProducerConfig.CLIENT_ID_CONFIG] = environment.getStringProperty("kafka.clientId")
                it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
                it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
                it[ProducerConfig.ACKS_CONFIG] = "all"
            })
        }
        single<AccountRepository> { AccountRepository(get()) }
        single<AccountService> { AccountService(get()) }
        single<AccountTransactionRepository> { AccountTransactionRepository(get()) }
        single<AccountTransactionService> { AccountTransactionService(get(), get()) }
    }