package ro.jf.funds.client.notebook

import com.charleskorn.kaml.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
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
import kotlin.time.Duration.Companion.seconds

class FundsClient(
    private val userSdk: UserSdk = UserSdk(),
    private val accountSdk: AccountSdk = AccountSdk(),
    private val fundSdk: FundSdk = FundSdk(),
    private val fundTransactionSdk: FundTransactionSdk = FundTransactionSdk(),
    private val importSdk: ImportSdk = ImportSdk(),
    private val reportingSdk: ReportingSdk = ReportingSdk(),
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            anchorsAndAliases = AnchorsAndAliases.Permitted(maxAliasCount = 50u)
        )
    )

    // TODO(Johann) would make sense to have these as non suspend functions, but then we would need to handle
    suspend fun ensureUserExists(username: String): UserTO {
        return userSdk.findUserByUsername(username)
            ?: userSdk.createUser(username)
    }

    suspend fun provisionAccounts(user: UserTO, yamlFile: File): List<AccountTO> {
        val existingAccounts = accountSdk.listAccounts(user.id).items
        val existingAccountNames = existingAccounts.map { it.name }.toSet()
        val newAccounts = yamlFile.readText()
            .let { yaml.decodeFromString(ListSerializer(CreateAccountTO.serializer()), it) }
            .filter { it.name !in existingAccountNames }
            .map { accountSdk.createAccount(user.id, it) }
        return existingAccounts + newAccounts
    }

    suspend fun provisionFunds(user: UserTO, yamlFile: File): List<FundTO> {
        val existingFunds = fundSdk.listFunds(user.id).items
        val existingFundNames = existingFunds.map { it.name }.toSet()
        val newFunds = yamlFile.readText()
            .let { yaml.decodeFromString(ListSerializer(CreateFundTO.serializer()), it) }
            .filter { it.name !in existingFundNames }
            .map { fundSdk.createFund(user.id, it) }
        return existingFunds + newFunds
    }

    suspend fun provisionInitialBalances(
        user: UserTO,
        accounts: List<AccountTO>,
        funds: List<FundTO>,
        yamlFile: File,
    ): List<FundTransactionTO> {
        val initialBalances: InitialBalances =
            yaml.decodeFromString<InitialBalances>(yamlFile.readText())

        val dateTime = LocalDateTime(initialBalances.date, LocalTime.parse("00:00"))
        val transactionRequests = initialBalances.balances.map { initialBalance ->
            val fund = funds.firstOrNull { it.name.value == initialBalance.fundName }
                ?: error("Fund '${initialBalance.fundName}' not found")
            val account = accounts.firstOrNull { it.name.value == initialBalance.accountName }
                ?: error("Account '${initialBalance.accountName}' not found")
            CreateFundTransactionTO(
                dateTime = dateTime,
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
        return transactions
    }

    suspend fun importTransactions(
        user: UserTO,
        importConfigurationYamlFile: File,
        csvFiles: List<File>,
    ): ImportTaskTO {
        val importConfiguration =
            yaml.decodeFromString<ImportConfigurationTO>(importConfigurationYamlFile.readText())

        var importTask = importSdk.import(user.id, importConfiguration, csvFiles)
        val now: Instant = Clock.System.now()
        val timeout = 120.seconds
        while (importTask.status == ImportTaskTO.Status.IN_PROGRESS && Clock.System.now() - now < timeout) {
            delay(500)
            importTask = importSdk.getImportTask(user.id, importTask.taskId)
        }
        return importTask
    }

    suspend fun createReportView(
        user: UserTO,
        reportViewName: String,
        fundName: String,
        reportDataConfigurationYamlFile: File,
        yamlPath: String? = null,
    ): ReportViewTO {
        val existingReportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportViewName }
        if (existingReportView != null) {
            return existingReportView
        }
        val fund = fundSdk.getFundByName(user.id, FundName(fundName))
            ?: error("Fund with name '$fundName' not found for user ${user.username}")
        val yamlNode = extractYamlNode(reportDataConfigurationYamlFile, yamlPath)
        val dataConfiguration =
            yaml.decodeFromYamlNode<ReportDataConfigurationTO>(yamlNode)
        val request = CreateReportViewTO(reportViewName, fund.id, dataConfiguration)
        var task: ReportViewTaskTO = reportingSdk.createReportView(user.id, request)
        val timeout = Clock.System.now().plus(120.seconds)
        while (task.status == ReportViewTaskStatus.IN_PROGRESS && Clock.System.now() < timeout) {
            delay(2000)
            task = reportingSdk.getReportViewTask(user.id, task.taskId)
        }
        return when (task.status) {
            ReportViewTaskStatus.COMPLETED -> {
                task.report ?: error("No report found on completed report task")
            }

            ReportViewTaskStatus.FAILED -> {
                throw IllegalStateException("Report view creation failed on task $task")
            }

            else -> {
                throw IllegalStateException("Report view creation timed out on task $task")
            }
        }
    }

    suspend fun getYearlyReportViewData(
        user: UserTO, reportName: String, fromYear: Int, toYear: Int, forecastUntilYear: Int? = null,
    ): ReportDataTO {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        return reportingSdk.getYearlyReportViewData(user.id, reportView.id, fromYear, toYear, forecastUntilYear)
    }

    suspend fun getMonthlyReportViewData(
        user: UserTO,
        reportName: String,
        fromYearMonth: YearMonthTO,
        toYearMonth: YearMonthTO,
        forecastUntilYearMonth: YearMonthTO? = null,
    ): ReportDataTO {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        return reportingSdk.getMonthlyReportViewData(
            user.id, reportView.id, fromYearMonth, toYearMonth, forecastUntilYearMonth
        )
    }

    suspend fun getDailyReportViewData(
        user: UserTO,
        reportName: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        forecastUntilDate: LocalDate? = null,
    ): ReportDataTO {
        val reportView = reportingSdk.listReportViews(user.id).items.firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
        return reportingSdk.getDailyReportViewData(user.id, reportView.id, fromDate, toDate, forecastUntilDate)
    }

    // TODO(Johann) library yaml support could be rethinked. maybe a method that would map a File and path to a TO. might be more flexible
    private fun extractYamlNode(yamlFile: File, path: String? = null): YamlNode {
        val root = yaml.parseToYamlNode(yamlFile.readText()) as YamlMap
        return if (path == null) {
            root
        } else {
            root.get<YamlMap>(path)
                ?: error("Path '$path' not found in YAML file '${yamlFile.name}'")
        }
    }
}
