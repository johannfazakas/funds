package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.module
import ro.jf.funds.platform.jvm.config.getEnvironmentProperty
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.model.GenericResponse
import ro.jf.funds.platform.jvm.persistence.getDataSource
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
import ro.jf.funds.fund.service.service.AccountService
import ro.jf.funds.fund.service.service.FundService
import ro.jf.funds.fund.service.service.RecordService
import ro.jf.funds.fund.service.service.TransactionService
import ro.jf.funds.fund.service.service.event.CreateTransactionsRequestHandler
import javax.sql.DataSource

val CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER: Qualifier =
    StringQualifier("CreateFundTransactionsResponse")

val Application.fundDependencies
    get() = module {
        includes(
            fundPersistenceDependencies,
            fundEventProducerDependencies,
            fundServiceDependencies,
            fundEventConsumerDependencies,
        )
    }

private val Application.fundPersistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<FundRepository> { FundRepository(get()) }
        single<AccountRepository> { AccountRepository(get()) }
        single<RecordRepository> { RecordRepository(get()) }
        single<TransactionRepository> { TransactionRepository(get()) }
    }


private val Application.fundEventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<GenericResponse>>(CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER) {
            createProducer(get(), get<TopicSupplier>().topic(FundEvents.FundTransactionsBatchResponse))
        }
    }

private val Application.fundServiceDependencies
    get() = module {
        single<AccountService> { AccountService(get(), get()) }
        single<TransactionService> { TransactionService(get(), get(), get()) }
        single<FundService> { FundService(get()) }
        single<RecordService> { RecordService(get()) }
        single<CreateTransactionsRequestHandler> {
            CreateTransactionsRequestHandler(
                get(),
                get<Producer<GenericResponse>>(CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER)
            )
        }

    }

private val Application.fundEventConsumerDependencies
    get() = module {

        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<Consumer<CreateTransactionsTO>> {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(FundEvents.FundTransactionsBatchRequest),
                get<CreateTransactionsRequestHandler>()
            )
        }
    }
