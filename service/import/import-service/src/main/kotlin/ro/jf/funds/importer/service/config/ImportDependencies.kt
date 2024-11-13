package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.event.ProducerProperties
import ro.jf.funds.commons.event.TopicSupplier
import ro.jf.funds.commons.event.createProducer
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.ImportFundMapper
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser
import java.sql.Connection
import javax.sql.DataSource

private const val ACCOUNT_SERVICE_BASE_URL_PROPERTY = "integration.account-service.base-url"
private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"

val Application.importDependencies
    get() = module {
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
        single<ImportFundMapper> { ImportFundMapper(get(), get(), get()) }
        single<ImportService> { ImportService(get(), get(), get(), get()) }
    }
