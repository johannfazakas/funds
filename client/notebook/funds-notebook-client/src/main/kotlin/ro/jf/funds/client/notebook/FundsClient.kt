package ro.jf.funds.client.notebook

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMap
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.internal.dataframe.DataFramePlotBuilder
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.area
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.kandy.util.context.invoke
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.sdk.AccountSdk
import ro.jf.funds.client.notebook.model.InitialBalances
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.LabelSdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.sdk.ImportSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.sdk.ReportingSdk
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.sdk.UserSdk
import java.io.File
import java.util.*
import java.math.BigDecimal as JavaBigDecimal
import kotlin.time.Duration.Companion.seconds

class FundsClient(
    private val userSdk: UserSdk = UserSdk(),
    private val accountSdk: AccountSdk = AccountSdk(),
    private val fundSdk: FundSdk = FundSdk(),
    private val labelSdk: LabelSdk = LabelSdk(),
    private val transactionSdk: TransactionSdk = TransactionSdk(),
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

    fun provisionLabels(user: UserTO, labels: List<CreateLabelTO>): List<LabelTO> = run {
        val existingLabels = labelSdk.listLabels(user.id)
        val existingLabelNames = existingLabels.map { it.name }.toSet()
        val newLabels = labels
            .filter { it.name !in existingLabelNames }
            .map { labelSdk.createLabel(user.id, it) }
        existingLabels + newLabels
    }

    fun provisionInitialBalances(
        user: UserTO,
        accounts: List<AccountTO>,
        funds: List<FundTO>,
        initialBalances: InitialBalances,
    ): List<TransactionTO> = run {
        val dateTime = LocalDateTime(initialBalances.date, LocalTime.parse("00:00"))
        val transactionRequests = initialBalances.balances.map { initialBalance ->
            val fund = funds.firstOrNull { it.name.value == initialBalance.fundName }
                ?: error("Fund '${initialBalance.fundName}' not found")
            val account = accounts.firstOrNull { it.name.value == initialBalance.accountName }
                ?: error("Account '${initialBalance.accountName}' not found")
            CreateTransactionTO.SingleRecord(
                dateTime = dateTime,
                externalId = listOf(dateTime, initialBalance.accountName, initialBalance.amount).joinToString()
                    .let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() },
                record = CreateTransactionRecordTO.CurrencyRecord(
                    fundId = fund.id,
                    accountId = account.id,
                    amount = BigDecimal.parseString(initialBalance.amount),
                    unit = account.unit as Currency
                )
            )
        }
        val transactions = transactionRequests.map { request ->
            transactionSdk.createTransaction(user.id, request)
        }
        transactions
    }

    fun importTransactions(
        user: UserTO,
        fileType: ImportFileTypeTO,
        configurationRequest: CreateImportConfigurationRequest,
        csvFiles: List<File>,
    ): List<ImportFileTO> = run {
        val existingConfigurations = importSdk.listImportConfigurations(user.id)
        val configuration = existingConfigurations.items.firstOrNull { it.name == configurationRequest.name }
            ?: importSdk.createImportConfiguration(user.id, configurationRequest)

        val importFiles = csvFiles.map { csvFile ->
            val createResponse = importSdk.createImportFile(
                user.id,
                CreateImportFileRequest(
                    fileName = csvFile.name,
                    type = fileType,
                    importConfigurationId = configuration.importConfigurationId,
                )
            )
            importSdk.uploadFile(createResponse.uploadUrl, csvFile.readBytes())
            importSdk.confirmUpload(user.id, createResponse.importFileId)
            importSdk.importFile(user.id, createResponse.importFileId)
            createResponse.importFileId
        }

        val timeout = 120.seconds
        val now: Instant = Clock.System.now()
        importFiles.map { importFileId ->
            var importFile = importSdk.getImportFile(user.id, importFileId)
            while (importFile.status == ImportFileStatusTO.IMPORTING && Clock.System.now() - now < timeout) {
                delay(500)
                importFile = importSdk.getImportFile(user.id, importFileId)
            }
            importFile
        }
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

    fun getReportNetData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ): ReportDataTO<NetReportTO> = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getNetData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportGroupedNetData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ) = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getGroupedNetData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportValueData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ): ReportDataTO<ValueReportTO> = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getValueData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportGroupedBudgetData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ) = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getGroupedBudgetData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportPerformanceData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ): ReportDataTO<PerformanceReportTO> = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getPerformanceData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportInstrumentPerformanceData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ) = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getInstrumentPerformanceData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportInterestRateData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ): ReportDataTO<InterestRateReportTO> = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getInterestRateData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun getReportInstrumentInterestRateData(
        user: UserTO, reportName: String, reportDataIntervalTO: ReportDataIntervalTO,
    ) = run {
        val reportView = getReportViewByName(user, reportName)
        reportingSdk.getInstrumentInterestRateData(user.id, reportView.id, reportDataIntervalTO)
    }

    fun <T> plotReportData(
        title: String,
        reportData: ReportDataTO<T>,
        plottedLines: Map<Color, (T) -> JavaBigDecimal> = emptyMap(),
        plottedAreas: Map<Color, (T) -> JavaBigDecimal> = emptyMap(),
    ): Plot {
        val plottedData = plottedLines + plottedAreas
        val dataFrame = plottedData
            .map { (color, dataMapper) ->
                color.toString() to reportData.timeBuckets.map { data -> dataMapper(data.report) }
            }
            .plus("timeBucket" to reportData.timeBuckets.map { it.timeBucket.from })
            .let { dataFrameOf(*it.toTypedArray()) }

        return dataFrame
            .plot {
                plotForecastBorderLine(plottedData, dataFrame, reportData)
                plotTimeAxis(reportData)
                line {
                    y.constant(0)
                }
                plottedLines.forEach { (color, _) ->
                    line {
                        y(color.toString())
                        this.color = color
                    }
                }
                plottedAreas.forEach { (color, _) ->
                    area {
                        y(color.toString())
                        borderLine {
                            this.color = color
                        }
                    }
                }
                layout {
                    this.title = title
                    size = 2400 to 800
                }
            }
    }

    private fun DataFramePlotBuilder<Any?>.plotTimeAxis(reportData: ReportDataTO<*>) {
        x("timeBucket") {
            val format = when (reportData.interval.granularity) {
                TimeGranularityTO.YEARLY -> "%Y"
                TimeGranularityTO.MONTHLY -> "%b %Y"
                TimeGranularityTO.DAILY -> "%d %b %Y"
            }
            axis.breaks(
                reportData.timeBuckets
                    .map { it.timeBucket.from.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }, format
            )
        }
    }

    private fun <T> DataFramePlotBuilder<Any?>.plotForecastBorderLine(
        plottedData: Map<Color, (T) -> JavaBigDecimal>,
        dataFrame: DataFrame<*>,
        reportData: ReportDataTO<T>,
    ) {
        line {
            val values = plottedData.keys
                .flatMap { dataFrame[it.toString()].values() }
                .map { it as JavaBigDecimal }
            val forecastBorderMin = values.minOrNull()?.takeIf { it < JavaBigDecimal.ZERO } ?: JavaBigDecimal.ZERO
            val forecastBorderMax = values.maxOrNull()?.takeIf { it > JavaBigDecimal.ZERO } ?: JavaBigDecimal.ZERO
            val forecastBorderX = when (reportData.interval.granularity) {
                TimeGranularityTO.YEARLY -> reportData.interval.toDate.minus(183, DateTimeUnit.DAY)
                TimeGranularityTO.MONTHLY -> reportData.interval.toDate.minus(15, DateTimeUnit.DAY)
                TimeGranularityTO.DAILY -> reportData.interval.toDate.minus(1, DateTimeUnit.DAY)
            }
            y(listOf(forecastBorderMin, forecastBorderMax))
            x.constant(forecastBorderX.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        }
    }

    fun plotReport(): Plot {
        val averageTemperature = dataFrameOf(
            "city" to listOf("New York", "London", "Berlin", "Yerevan", "Tokyo"),
            "average temperature" to listOf(12.5, 11.0, 9.6, 11.5, 16.0)
        )

        // Construct a plot using the reportdata from the DataFrame
        return averageTemperature.plot {
            // Add bars to the plot
            // Each bar represents the average temperature in a city
            bars {
                x("city") // Set the cities' reportdata on the X-axis
                y("average temperature") { // Set the temperatures' reportdata on the Y-axis
                    axis.name = "Average Temperature (°C)" // Assign a name to the Y-axis
                }
            }
            // Set the title of the plot
            layout.title = "Kandy Getting Started Example"
        }
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

    private suspend fun getReportViewByName(user: UserTO, reportName: String): ReportViewTO {
        return reportingSdk.listReportViews(user.id).items
            .firstOrNull { it.name == reportName }
            ?: error("Report view with name '$reportName' not found for user ${user.username}")
    }

    private fun <T> run(block: suspend () -> T): T = scope.future { block() }.join()
}
