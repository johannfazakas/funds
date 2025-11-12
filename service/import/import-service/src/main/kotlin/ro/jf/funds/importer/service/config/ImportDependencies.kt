package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.bind
import org.koin.dsl.module
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.fund.sdk.AccountSdk
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.conversion.AccountService
import ro.jf.funds.importer.service.service.conversion.FundService
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter
import ro.jf.funds.importer.service.service.conversion.strategy.*
import ro.jf.funds.importer.service.service.event.CreateFundTransactionsResponseHandler
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.FundsFormatImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser
import javax.sql.DataSource

private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"
private const val CONVERSION_SERVICE_BASE_URL_PROPERTY = "integration.conversion-service.base-url"

val CREATE_FUND_TRANSACTIONS_RESPONSE_CONSUMER = StringQualifier("CreateFundTransactionsResponse")

val Application.importDependencyModules
    get() = arrayOf(
        importPersistenceDependencies,
        importIntegrationDependencies,
        importEventProducerDependencies,
        importServiceDependencies,
        importEventConsumerDependencies,
    )

private val Application.importPersistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<ImportTaskRepository> { ImportTaskRepository(get()) }
    }

private val Application.importIntegrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
        single<AccountSdk> {
            AccountSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<FundSdk> {
            FundSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<TransactionSdk> {
            TransactionSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<ConversionSdk> {
            ConversionSdk(environment.getStringProperty(CONVERSION_SERVICE_BASE_URL_PROPERTY))
        }
    }

private val Application.importEventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<CreateTransactionsTO>> {
            createProducer(get(), get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST))
        }
    }

private val Application.importServiceDependencies
    get() = module {
        single<CsvParser> { CsvParser() }
        single<WalletCsvImportParser> { WalletCsvImportParser(get()) }
        single<FundsFormatImportParser> { FundsFormatImportParser(get()) }
        single<ImportParserRegistry> { ImportParserRegistry(get(), get()) }
        single<AccountService> { AccountService(get()) }
        single<FundService> { FundService(get()) }
        single<SingleRecordTransactionConverter> { SingleRecordTransactionConverter() } bind ImportTransactionConverter::class
        single<TransferTransactionConverter> { TransferTransactionConverter() } bind ImportTransactionConverter::class
        single<ExchangeSingleTransactionConverter> { ExchangeSingleTransactionConverter() } bind ImportTransactionConverter::class
        single<InvestmentTransactionConverter> { InvestmentTransactionConverter() } bind ImportTransactionConverter::class
        single<ImportTransactionConverterRegistry> { ImportTransactionConverterRegistry(getAll()) }
        single<ImportFundConversionService> { ImportFundConversionService(get(), get(), get(), get()) }
        single<ImportService> { ImportService(get(), get(), get(), get()) }
        single<CreateFundTransactionsResponseHandler> {
            CreateFundTransactionsResponseHandler(get())
        }
    }

private val Application.importEventConsumerDependencies
    get() = module {

        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }

        single<Consumer<GenericResponse>>(CREATE_FUND_TRANSACTIONS_RESPONSE_CONSUMER) {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE),
                get<CreateFundTransactionsResponseHandler>()
            )
        }
    }
