package ro.jf.funds.importer.service.service.parser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.exception.ImportDataException

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    @Test
    fun `should parse simple wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")))
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Expenses"))
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("-13.80".toBigDecimal())
    }

    @Test
    fun `should parse transfer wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Cash RON", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING")),
                AccountMatcherTO("Cash RON", AccountName("Cash"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")),
                FundMatcherTO.ByLabel("Basic - Food", FundName("Income")),
                FundMatcherTO.ByAccount("ING old", FundName("Expenses")),
                FundMatcherTO.ByAccount("Cash RON", FundName("Expenses"))
            )
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(2)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("-400.00".toBigDecimal())
        assertThat(importTransactions[0].records[1].accountName).isEqualTo(AccountName("Cash"))
        assertThat(importTransactions[0].records[1].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[1].amount).isEqualTo("400.00".toBigDecimal())
    }

    @Test
    fun `should parse wallet csv import item with implicit fund transfer`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "6740.00", "Work Income", "2019-01-06 02:00:23")
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccountLabelWithTransfer("ING old", importLabel = "Work Income", FundName("Work"), FundName("Expenses")),
            )
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[0].records).hasSize(3)

        assertThat(importTransactions[0].records[0].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[0].fundName).isEqualTo(FundName("Work"))
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("6740.00".toBigDecimal())

        assertThat(importTransactions[0].records[1].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[1].fundName).isEqualTo(FundName("Work"))
        assertThat(importTransactions[0].records[1].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[1].amount).isEqualTo("-6740.00".toBigDecimal())

        assertThat(importTransactions[0].records[2].accountName).isEqualTo(AccountName("ING"))
        assertThat(importTransactions[0].records[2].fundName).isEqualTo(FundName("Expenses"))
        assertThat(importTransactions[0].records[2].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[2].amount).isEqualTo("6740.00".toBigDecimal())
    }

    @Test
    fun `should use first matching fund when multiple fund matchers are matching`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")),
                FundMatcherTO.ByAccount("ING old", FundName("Savings"))
            )
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
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(AccountMatcherTO("ING new", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")))
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
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(AccountMatcherTO("ING old", AccountName("ING"))),
            fundMatchers = listOf(FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")))
        )

        assertThatThrownBy { walletCsvImportParser.parse(importConfiguration, listOf(fileContent)) }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("No import data")
    }

    private data class WalletCsvRowContent(
        val accountName: String,
        val currency: String,
        val amount: String,
        val label: String,
        val date: String
    )

    private fun generateFileContent(vararg rowContent: WalletCsvRowContent): String {
        val header = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
        """.trimIndent()
        val rows = rowContent.joinToString("\n") { (accountName, currency, amount, label, date) ->
            "$accountName;Groceries;$currency;$amount;$amount;Expenses;TRANSFER;Bank transfer;Cumparare POS SEREDEF SRL DEP RO CLUJ-NAPOCA;$date;;;;0;false;;$label;1000;false"
        }
        return "$header\n$rows"
    }
}
