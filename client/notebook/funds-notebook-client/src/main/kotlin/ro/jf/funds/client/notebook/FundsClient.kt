package ro.jf.funds.client.notebook

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.datetime.*
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.client.notebook.model.InitialBalances
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.sdk.ImportSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.sdk.ReportingSdk
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.sdk.UserSdk
import java.io.File
import java.math.BigDecimal
import java.util.*
import kotlin.time.Duration.Companion.seconds

class FundsClient(
    private val userSdk: UserSdk = UserSdk(),
    private val accountSdk: AccountSdk = AccountSdk(),
    private val fundSdk: FundSdk = FundSdk(),
    private val fundTransactionSdk: FundTransactionSdk = FundTransactionSdk(),
    private val importSdk: ImportSdk = ImportSdk(),
    private val reportingSdk: ReportingSdk = ReportingSdk(),
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    val yaml = Yaml(
        configuration = YamlConfiguration(
            anchorsAndAliases = AnchorsAndAliases.Permitted(maxAliasCount = 50u)
        )
    )

    fun ensureUserExists(username: String): UserTO = run {
        userSdk.findUserByUsername(username) ?: userSdk.createUser(username)
    }

    fun provisionAccounts(user: UserTO, accounts: List<CreateAccountTO>): List<AccountTO> = run {
        val existingAccounts = accountSdk.listAccounts(user.id).items
        val existingAccountNames = existingAccounts.map { it.name }.toSet()
        val newAccounts = accounts
            .filter { it.name !in existingAccountNames }
            .map { accountSdk.createAccount(user.id, it) }
        existingAccounts + newAccounts
    }

    fun provisionFunds(user: UserTO, funds: List<CreateFundTO>): List<FundTO> = run {
        val existingFunds = fundSdk.listFunds(user.id).items
        val existingFundNames = existingFunds.map { it.name }.toSet()
        val newFunds = funds
            .filter { it.name !in existingFundNames }
            .map { fundSdk.createFund(user.id, it) }
        existingFunds + newFunds
    }

    fun provisionInitialBalances(
        user: UserTO,
        accounts: List<AccountTO>,
        funds: List<FundTO>,
        initialBalances: InitialBalances,
    ): List<FundTransactionTO> = run {
        val dateTime = LocalDateTime(initialBalances.date, LocalTime.parse("00:00"))
        val transactionRequests = initialBalances.balances.map { initialBalance ->
            val fund = funds.firstOrNull { it.name.value == initialBalance.fundName }
                ?: error("Fund '${initialBalance.fundName}' not found")
            val account = accounts.firstOrNull { it.name.value == initialBalance.accountName }
                ?: error("Account '${initialBalance.accountName}' not found")
            CreateFundTransactionTO(
                dateTime = dateTime,
                externalId = listOf(dateTime, initialBalance.accountName, initialBalance.amount).joinToString()
                    .let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() },
                records = listOf(
                    CreateFundRecordTO(
                        fundId = fund.id,
                        accountId = account.id,
                        amount = BigDecimal(initialBalance.amount),
                        unit = account.unit
                    )
                )
            )
        }
        val transactions = transactionRequests.map { request ->
            fundTransactionSdk.createTransaction(user.id, request)
        }
        transactions
    }

    fun importTransactions(
        user: UserTO,
        importConfiguration: ImportConfigurationTO,
        csvFiles: List<File>,
    ): ImportTaskTO = run {
        var importTask = importSdk.import(user.id, importConfiguration, csvFiles)
        val now: Instant = Clock.System.now()
        val timeout = 120.seconds
        while (importTask.status == ImportTaskTO.Status.IN_PROGRESS && Clock.System.now() - now < timeout) {
            delay(500)
            importTask = importSdk.getImportTask(user.id, importTask.taskId)
        }
        importTask
    }

    fun createReportView(
        user: UserTO,
        reportViewName: String,
        fundName: String,
        dataConfiguration: ReportDataConfigurationTO,
    ): ReportViewTO = run {
        val existingReportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportViewName }
        if (existingReportView != null) {
            return@run existingReportView
        }
        val fund = fundSdk.getFundByName(user.id, FundName(fundName))
            ?: error("Fund with name '$fundName' not found for user ${user.username}")
        val request = CreateReportViewTO(reportViewName, fund.id, dataConfiguration)
        reportingSdk.createReportView(user.id, request)
    }

    fun getYearlyReportViewData(
        user: UserTO, reportName: String, fromYear: Int, toYear: Int, forecastUntilYear: Int? = null,
    ): ReportDataTO = run {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        reportingSdk.getYearlyReportViewData(user.id, reportView.id, fromYear, toYear, forecastUntilYear)
    }

    fun getMonthlyReportViewData(
        user: UserTO,
        reportName: String,
        fromYearMonth: YearMonthTO,
        toYearMonth: YearMonthTO,
        forecastUntilYearMonth: YearMonthTO? = null,
    ): ReportDataTO = run {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        reportingSdk.getMonthlyReportViewData(
            user.id, reportView.id, fromYearMonth, toYearMonth, forecastUntilYearMonth
        )
    }

    fun getDailyReportViewData(
        user: UserTO,
        reportName: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        forecastUntilDate: LocalDate? = null,
    ): ReportDataTO = run {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        reportingSdk.getDailyReportViewData(user.id, reportView.id, fromDate, toDate, forecastUntilDate)
    }

    inline fun <reified T> fromYaml(yamlFile: File, path: String? = null): T {
        val root = yaml.parseToYamlNode(yamlFile.readText())
        val yamlNode = if (path == null) {
            root
        } else {
            (root as YamlMap).get<YamlMap>(path)
                ?: error("Path '$path' not found in YAML file '${yamlFile.name}'")
        }
        return yaml.decodeFromYamlNode(yamlNode)
    }

    private fun <T> run(block: suspend () -> T): T = scope.future { block() }.join()
}
