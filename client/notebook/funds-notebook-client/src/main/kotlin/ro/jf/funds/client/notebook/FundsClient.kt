package ro.jf.funds.client.notebook

import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
) {
    suspend fun ensureUserExists(username: String): UserTO {
        return userSdk.findUserByUsername(username)
            ?: userSdk.createUser(username)
    }

    suspend fun provisionAccounts(user: UserTO, yamlFile: File): List<AccountTO> {
        val existingAccounts = accountSdk.listAccounts(user.id).items
        val existingAccountNames = existingAccounts.map { it.name }.toSet()
        val newAccounts = yamlFile.readText()
            .let { Yaml.default.decodeFromString(ListSerializer(CreateAccountTO.serializer()), it) }
            .filter { it.name !in existingAccountNames }
            .map { accountSdk.createAccount(user.id, it) }
        return existingAccounts + newAccounts
    }

    suspend fun provisionFunds(user: UserTO, yamlFile: File): List<FundTO> {
        val existingFunds = fundSdk.listFunds(user.id).items
        val existingFundNames = existingFunds.map { it.name }.toSet()
        val newFunds = yamlFile.readText()
            .let { Yaml.default.decodeFromString(ListSerializer(CreateFundTO.serializer()), it) }
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
            Yaml.default.decodeFromString<InitialBalances>(yamlFile.readText())

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
            Yaml.default.decodeFromString<ImportConfigurationTO>(importConfigurationYamlFile.readText())

        var importTask = importSdk.import(user.id, importConfiguration, csvFiles)
        val now: Instant = Clock.System.now()
        val timeout = 120.seconds
        while (importTask.status == ImportTaskTO.Status.IN_PROGRESS && Clock.System.now() - now < timeout) {
            delay(500)
            importTask = importSdk.getImportTask(user.id, importTask.taskId)
        }
        return importTask
    }
}
