package ro.jf.funds.importer.service.service.parser

import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.fund.api.model.FundName
import com.benasher44.uuid.uuid4
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    @Test
    fun `should parse simple wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses"))),
            labelMatchers = listOf(LabelMatcherTO(listOf("Basic - Food"), Label("Basic"))),
            exchangeMatchers = emptyList(),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Expenses"))
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("-13.80"))
        assertThat(importTransactions[0].records[0].labels).containsExactly(Label("Basic"))
    }

    @Test
    fun `should parse transfer wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Cash RON", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING")),
                AccountMatcherTO("Cash RON", AccountName("Cash"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses")),
                FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Income")),
                FundMatcherTO.ByAccount(listOf("ING old", "Cash RON"), FundName("Expenses"))
            ),
            exchangeMatchers = emptyList(),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(2)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("-400.00"))
        assertThat(importTransactions[0].records[1].accountName).isEqualTo(AccountName("Cash"))
        assertThat(importTransactions[0].records[1].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[1].amount).isEqualTo(BigDecimal.parseString("400.00"))
    }

    @Test
    fun `should parse currency exchange transfer`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("Euro", "EUR", "-1.89", "Exchange", "2019-04-23 21:45:02", "exchange"),
            WalletCsvRowContent("Cash RON", "RON", "-1434.00", "Exchange", "2019-04-23 21:45:49", "exchange"),
            WalletCsvRowContent("Euro", "EUR", "301.24", "Exchange", "2019-04-23 21:45:49", "exchange"),
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(
                AccountMatcherTO("Euro", AccountName("Cash EUR")),
                AccountMatcherTO("Cash RON", AccountName("Cash RON"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccount(listOf("Euro", "Cash RON"), FundName("Expenses"))
            ),
            exchangeMatchers = listOf(ExchangeMatcherTO.ByLabel("Exchange")),
            labelMatchers = listOf(LabelMatcherTO(listOf("Exchange"), Label("Exchange"))),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-04-23T21:45:02")
        assertThat(importTransactions[0].records).hasSize(3)
        assertThat(importTransactions[0].records).containsExactlyInAnyOrder(
            ImportParsedRecord(
                AccountName("Cash EUR"),
                FundName("Expenses"),
                Currency.EUR,
                BigDecimal.parseString("-1.89"),
                listOf(Label("Exchange")),
                note = "exchange",
            ),
            ImportParsedRecord(
                AccountName("Cash RON"),
                FundName("Expenses"),
                Currency.RON,
                BigDecimal.parseString("-1434.00"),
                listOf(Label("Exchange")),
                note = "exchange",
            ),
            ImportParsedRecord(
                AccountName("Cash EUR"),
                FundName("Expenses"),
                Currency.EUR,
                BigDecimal.parseString("301.24"),
                listOf(Label("Exchange")),
                note = "exchange",
            )
        )
    }

    @Test
    fun `should parse wallet csv import item with implicit fund transfer based on label`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "740.00", "Gift income", "2019-01-06 02:00:23")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabelWithPostTransfer(
                    importLabels = listOf("Gift income"), FundName("Gift income"), FundName("Expenses")
                ),
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcherTO(listOf("Gift income"), Label("gifts"))),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(2)

        // Main transaction
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Gift income"))
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("740.00"))

        // Transfer transaction
        assertThat(importTransactions[1].transactionExternalId).endsWith("-fund-transfer")
        assertThat(importTransactions[1].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[1].records).hasSize(2)
        assertThat(importTransactions[1].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[1].records[0].fundName).isEqualTo(FundName("Gift income"))
        assertThat(importTransactions[1].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[0].amount).isEqualTo(BigDecimal.parseString("-740.00"))

        assertThat(importTransactions[1].records[1].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[1].records[1].fundName).isEqualTo(FundName("Expenses"))
        assertThat(importTransactions[1].records[1].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[1].amount).isEqualTo(BigDecimal.parseString("740.00"))
    }

    @Test
    fun `should parse wallet csv import item with implicit fund transfer based on account and label`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "6740.00", "Work Income", "2019-01-06 02:00:23")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccountLabelWithPostTransfer(
                    listOf("ING old"),
                    importLabels = listOf("Work Income"),
                    FundName("Work"),
                    FundName("Expenses")
                ),
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcherTO(listOf("Work Income"), Label("Work"))),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(2)

        // Main transaction
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Work"))
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("6740.00"))

        // Transfer transaction
        assertThat(importTransactions[1].transactionExternalId).endsWith("-fund-transfer")
        assertThat(importTransactions[1].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[1].records).hasSize(2)
        assertThat(importTransactions[1].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[1].records[0].fundName).isEqualTo(FundName("Work"))
        assertThat(importTransactions[1].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[0].amount).isEqualTo(BigDecimal.parseString("-6740.00"))

        assertThat(importTransactions[1].records[1].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[1].records[1].fundName).isEqualTo(FundName("Expenses"))
        assertThat(importTransactions[1].records[1].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[1].amount).isEqualTo(BigDecimal.parseString("6740.00"))
    }

    @Test
    fun `should use first matching fund when multiple fund matchers are matching`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses")),
                FundMatcherTO.ByAccount(listOf("ING old"), FundName("Savings"))
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcherTO(listOf("Basic - Food"), Label("Basic"))),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Expenses"))
    }

    @Test
    fun `should raise import data exception when account name not matched`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(AccountMatcherTO("ING new", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses"))),
            exchangeMatchers = emptyList(),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        assertThatThrownBy { walletCsvImportParser.parse(importConfiguration, listOf(fileContent)) }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Account name not matched: ING old")
    }

    @Test
    fun `should raise import data exception when empty import`() {
        val fileContent = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
        """.trimIndent()
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses"))),
            exchangeMatchers = emptyList(),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        assertThatThrownBy { walletCsvImportParser.parse(importConfiguration, listOf(fileContent)) }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("No import reportdata")
    }

    @Test
    fun `should skip transactions involving skipped account`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Skipped account", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            importConfigurationId = uuid4(),
            name = "test-config",
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING")),
                AccountMatcherTO("Skipped account", skipped = true)
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Expenses")),
                FundMatcherTO.ByLabel(listOf("Basic - Food"), FundName("Income")),
                FundMatcherTO.ByAccount(listOf("ING old", "Cash RON"), FundName("Expenses"))
            ),
            exchangeMatchers = emptyList(),
            createdAt = LocalDateTime.parse("2026-01-01T00:00:00"),
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(0)
    }

    private data class WalletCsvRowContent(
        val accountName: String,
        val currency: String,
        val amount: String,
        val label: String,
        val date: String,
        val note: String = StringUtils.EMPTY,
    )

    private fun generateFileContent(vararg rowContent: WalletCsvRowContent): String {
        val header = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
        """.trimIndent()
        val rows = rowContent.joinToString("\n") { (accountName, currency, amount, label, date, note) ->
            "$accountName;Groceries;$currency;$amount;$amount;Expenses;TRANSFER;Bank transfer;$note;$date;;;;0;false;;$label;1000;false"
        }
        return "$header\n$rows"
    }
}
