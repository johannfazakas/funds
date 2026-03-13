package ro.jf.funds.importer.service.service.parser

import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import com.ionspin.kotlin.bignum.decimal.BigDecimal

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    @Test
    fun `should parse simple wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            labelMatchers = listOf(LabelMatcher(listOf("Basic - Food"), Label("Basic"))),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
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
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher("ING old", AccountName("ING")),
                AccountMatcher("Cash RON", AccountName("Cash"))
            ),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importLabel = "Basic - Food"),
                FundMatcher(FundName("Income"), importLabel = "Basic - Food"),
                FundMatcher(FundName("Expenses"), importAccountName = "ING old"),
                FundMatcher(FundName("Expenses"), importAccountName = "Cash RON")
            ),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
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
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher("Euro", AccountName("Cash EUR")),
                AccountMatcher("Cash RON", AccountName("Cash RON"))
            ),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importAccountName = "Euro"),
                FundMatcher(FundName("Expenses"), importAccountName = "Cash RON")
            ),
            exchangeMatchers = listOf(ExchangeMatcher.ByLabel("Exchange")),
            labelMatchers = listOf(LabelMatcher(listOf("Exchange"), Label("Exchange"))),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
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
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importLabel = "Gift income", intermediaryFundName = FundName("Gift income")),
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcher(listOf("Gift income"), Label("gifts"))),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
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
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importAccountName = "ING old", importLabel = "Work Income", intermediaryFundName = FundName("Work")),
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcher(listOf("Work Income"), Label("Work"))),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
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
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING old", AccountName("ING"))),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importLabel = "Basic - Food"),
                FundMatcher(FundName("Savings"), importAccountName = "ING old")
            ),
            exchangeMatchers = emptyList(),
            labelMatchers = listOf(LabelMatcher(listOf("Basic - Food"), Label("Basic"))),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Expenses"))
    }

    @Test
    fun `given unmatched account name - when parsing - then returns error with account not matched`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING new", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems).containsExactly("Account name not matched: ING old")
    }

    @Test
    fun `given empty import - when parsing - then throws import data exception`() {
        val fileContent = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
        """.trimIndent()
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            exchangeMatchers = emptyList(),
        )

        assertThatThrownBy { walletCsvImportParser.parse(matchers, fileContent) }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("No import reportdata")
    }

    @Test
    fun `given skipped account in transaction - when parsing - then returns main transaction and implicit transfer error`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Skipped account", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher("ING old", AccountName("ING")),
                AccountMatcher("Skipped account", skipped = true)
            ),
            fundMatchers = listOf(
                FundMatcher(FundName("Expenses"), importLabel = "Basic - Food"),
                FundMatcher(FundName("Income"), importLabel = "Basic - Food"),
                FundMatcher(FundName("Expenses"), importAccountName = "ING old"),
                FundMatcher(FundName("Expenses"), importAccountName = "Cash RON")
            ),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).hasSize(1)
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems).hasSize(1)
        assertThat(errors[0].problems.first()).startsWith("Account skipped on implicit transfer:")
    }

    @Test
    fun `given multiple items with different unmatched accounts - when parsing - then returns all errors deduplicated`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("Unknown1", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Unknown2", "RON", "-25.00", "Basic - Food", "2019-01-31 03:00:00"),
            WalletCsvRowContent("Unknown1", "RON", "-10.00", "Basic - Food", "2019-01-31 04:00:00"),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        assertThat(results.failures().flatMap { it.problems }.toSet()).containsExactlyInAnyOrder(
            "Account name not matched: Unknown1",
            "Account name not matched: Unknown2",
        )
    }

    @Test
    fun `given mix of valid and invalid items - when parsing - then returns successful transactions and errors`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Unknown", "RON", "-25.00", "Basic - Food", "2019-01-31 03:00:00"),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            labelMatchers = listOf(LabelMatcher(listOf("Basic - Food"), Label("Basic"))),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        val transactions = results.successes()
        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems).containsExactly("Account name not matched: Unknown")
    }

    @Test
    fun `given unmatched fund matcher - when parsing - then returns error with fund matcher problem`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Unknown Label", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcher(FundName("Expenses"), importLabel = "Basic - Food")),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems.first()).contains("No fund matcher found")
    }

    private fun <T> List<Result<T>>.successes(): List<T> = mapNotNull { it.getOrNull() }
    private fun <T> List<Result<T>>.failures(): List<ImportDataException> =
        mapNotNull { it.exceptionOrNull() as? ImportDataException }

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
