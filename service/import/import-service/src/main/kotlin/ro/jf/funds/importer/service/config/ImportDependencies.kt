package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.module
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.conversion.HistoricalPricingAdapter
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.conversion.converter.*
import ro.jf.funds.importer.service.service.event.CreateFundTransactionsResponseHandler
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser
import java.sql.Connection
import javax.sql.DataSource

private const val ACCOUNT_SERVICE_BASE_URL_PROPERTY = "integration.account-service.base-url"
private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"
private const val HISTORICAL_PRICING_SERVICE_BASE_URL_PROPERTY = "integration.historical-pricing-service.base-url"

val CREATE_FUND_TRANSACTIONS_RESPONSE_CONSUMER = StringQualifier("CreateFundTransactionsResponse")

val Application.importDependencies: Module
    get() {
        return module {
            single<DataSource> { environment.getDataSource() }
            single<Database> { Database.connect(datasource = get()) }
            single<Connection> { environment.getDbConnection() }
            single<ImportTaskRepository> { ImportTaskRepository(get()) }
            single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
            single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
            single<Producer<CreateFundTransactionsTO>> {
                createProducer(get(), get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST))
            }
            single<HttpClient> { createHttpClient() }
            single<CsvParser> { CsvParser() }
            single<WalletCsvImportParser> { WalletCsvImportParser(get()) }
            single<ImportParserRegistry> { ImportParserRegistry(get()) }
            single<AccountSdk> {
                AccountSdk(environment.getStringProperty(ACCOUNT_SERVICE_BASE_URL_PROPERTY), get())
            }
            single<FundSdk> {
                FundSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
            }
            single<FundTransactionSdk> {
                FundTransactionSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
            }
            single<HistoricalPricingSdk> {
                HistoricalPricingSdk(environment.getStringProperty(HISTORICAL_PRICING_SERVICE_BASE_URL_PROPERTY))
            }
            single<HistoricalPricingAdapter> { HistoricalPricingAdapter(get()) }
            single<SingleRecordFundConverter> { SingleRecordFundConverter() }
            single<TransferFundConverter> { TransferFundConverter() }
            single<ImplicitTransferFundConverter> { ImplicitTransferFundConverter() }
            single<ExchangeSingleFundConverter> { ExchangeSingleFundConverter() }
            single<ImportFundConverterRegistry> { ImportFundConverterRegistry(get(), get(), get(), get()) }
            single<ImportFundConversionService> { ImportFundConversionService(get(), get(), get(), get()) }
            single<ImportService> { ImportService(get(), get(), get(), get()) }

            single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
            single<CreateFundTransactionsResponseHandler> {
                CreateFundTransactionsResponseHandler(get())
            }
            single<Consumer<GenericResponse>>(CREATE_FUND_TRANSACTIONS_RESPONSE_CONSUMER) {
                createConsumer(
                    get(),
                    get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE),
                    get<CreateFundTransactionsResponseHandler>()
                )
            }
        }
    }
