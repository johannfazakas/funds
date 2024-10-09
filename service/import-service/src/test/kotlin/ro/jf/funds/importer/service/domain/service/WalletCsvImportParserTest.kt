package ro.jf.funds.importer.service.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.model.*
import ro.jf.funds.importer.service.domain.service.parser.CsvParser
import ro.jf.funds.importer.service.domain.service.parser.WalletCsvImportParser

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    @Test
    fun `should parse simple wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = AccountMatchers(AccountMatcher("ING old", "ING")),
            fundMatchers = FundMatchers(FundMatcher.ByLabel("Basic - Food", "Expenses"))
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].date.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo("ING")
        assertThat(importTransactions[0].records[0].fundName).isEqualTo("Expenses")
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("-13.80".toBigDecimal())
    }

    @Test
    @Disabled("Handle fund recognition on transfers")
    fun `should parse transfer wallet csv import item`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Cash RON", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = AccountMatchers(
                listOf(
                    AccountMatcher("ING old", "ING"),
                    AccountMatcher("Cash RON", "Cash")
                )
            ),
            fundMatchers = FundMatchers(
                FundMatcher.ByLabel("Basic - Food", "Expenses"),
                FundMatcher.ByLabel("Basic - Food", "Income")
            )
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].date.toString()).isEqualTo("2019-01-29T02:00:37")
        assertThat(importTransactions[0].records).hasSize(2)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo("ING")
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("-400.00".toBigDecimal())
        assertThat(importTransactions[0].records[1].accountName).isEqualTo("Cash")
        assertThat(importTransactions[0].records[1].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[1].amount).isEqualTo("400.00".toBigDecimal())
    }

    @Test
    @Disabled
    fun `should parse wallet csv import item with implicit fund transfer`() {
        error("Not implemented")
    }

    @Test
    fun `should raise import data exception when multiple fund matchers are matching`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = AccountMatchers(AccountMatcher("ING old", "ING")),
            fundMatchers = FundMatchers(
                FundMatcher.ByLabel("Basic - Food", "Expenses"),
                FundMatcher.ByAccountLabel("ING old", "Basic - Food", "Savings")
            )
        )

        assertThatThrownBy { walletCsvImportParser.parse(importConfiguration, listOf(fileContent)) }
            .isInstanceOf(ImportDataException::class.java)
    }

    @Test
    fun `should raise import data exception when account name not matched`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = AccountMatchers(AccountMatcher("ING new", "ING")),
            fundMatchers = FundMatchers(FundMatcher.ByLabel("Basic - Food", "Expenses"))
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
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = AccountMatchers(AccountMatcher("ING old", "ING")),
            fundMatchers = FundMatchers(FundMatcher.ByLabel("Basic - Food", "Expenses"))
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
