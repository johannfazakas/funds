package ro.jf.funds.importer.service.config

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.bind
import org.koin.dsl.module
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.fund.sdk.AccountSdk
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.LabelSdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.ImportFileService
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.conversion.*
import ro.jf.funds.importer.service.service.conversion.strategy.*
import ro.jf.funds.importer.service.service.event.CreateFundTransactionsResponseHandler
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.FundsFormatImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser
import ro.jf.funds.platform.jvm.config.getEnvironmentProperty
import ro.jf.funds.platform.jvm.config.getStringProperty
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.model.GenericResponse
import ro.jf.funds.platform.jvm.persistence.getDataSource
import ro.jf.funds.platform.jvm.web.createHttpClient
import javax.sql.DataSource
import kotlin.time.Duration

private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"
private const val CONVERSION_SERVICE_BASE_URL_PROPERTY = "integration.conversion-service.base-url"
private const val S3_ENDPOINT_PROPERTY = "s3.endpoint"
private const val S3_REGION_PROPERTY = "s3.region"
private const val S3_BUCKET_PROPERTY = "s3.bucket"
private const val S3_ACCESS_KEY_PROPERTY = "s3.access-key"
private const val S3_SECRET_KEY_PROPERTY = "s3.secret-key"
private const val S3_PUBLIC_ENDPOINT_PROPERTY = "s3.public-endpoint"
private const val S3_PRESIGNED_URL_EXPIRATION_PROPERTY = "s3.presigned-url-expiration"

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
        single<ImportFileRepository> { ImportFileRepository(get()) }
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
        single<LabelSdk> {
            LabelSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<ConversionSdk> {
            ConversionSdk(environment.getStringProperty(CONVERSION_SERVICE_BASE_URL_PROPERTY))
        }
        single<S3Client> {
            S3Client {
                endpointUrl = Url.parse(environment.getStringProperty(S3_ENDPOINT_PROPERTY))
                region = environment.getStringProperty(S3_REGION_PROPERTY)
                credentialsProvider = object : CredentialsProvider {
                    override suspend fun resolve(attributes: Attributes) =
                        Credentials(
                            accessKeyId = environment.getStringProperty(S3_ACCESS_KEY_PROPERTY),
                            secretAccessKey = environment.getStringProperty(S3_SECRET_KEY_PROPERTY)
                        )
                }
                forcePathStyle = true
            }
        }
    }

private val Application.importEventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<CreateTransactionsTO>> {
            createProducer(get(), get<TopicSupplier>().topic(FundEvents.FundTransactionsBatchRequest))
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
        single<LabelService> { LabelService(get()) }
        single<SingleRecordTransactionConverter> { SingleRecordTransactionConverter() } bind ImportTransactionConverter::class
        single<TransferTransactionConverter> { TransferTransactionConverter() } bind ImportTransactionConverter::class
        single<ExchangeSingleTransactionConverter> { ExchangeSingleTransactionConverter() } bind ImportTransactionConverter::class
        single<InvestmentTransactionConverter> { InvestmentTransactionConverter() } bind ImportTransactionConverter::class
        single<ImportTransactionConverterRegistry> { ImportTransactionConverterRegistry(getAll()) }
        single<ImportFundConversionService> { ImportFundConversionService(get(), get(), get(), get(), get()) }
        single<ImportFileService> {
            ImportFileService(
                get(),
                get(),
                environment.getStringProperty(S3_BUCKET_PROPERTY),
                environment.getStringProperty(S3_ENDPOINT_PROPERTY),
                environment.getStringProperty(S3_PUBLIC_ENDPOINT_PROPERTY),
                Duration.parse(environment.getStringProperty(S3_PRESIGNED_URL_EXPIRATION_PROPERTY)),
            )
        }
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
                get<TopicSupplier>().topic(FundEvents.FundTransactionsBatchResponse),
                get<CreateFundTransactionsResponseHandler>()
            )
        }
    }
