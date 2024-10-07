package ro.jf.funds.importer.service.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.importer.service.domain.model.AccountMatcher
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportType
import ro.jf.funds.importer.service.domain.service.parser.CsvParser
import ro.jf.funds.importer.service.domain.service.parser.WalletCsvImportParser
import java.math.BigDecimal

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    @Test
    fun `should parse simple wallet csv import item`() {
        val fileContent = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
            ING old;Groceries;RON;-13.80;-13.80;Expenses;TRANSFER;Bank transfer;Cumparare POS SEREDEF SRL DEP RO CLUJ-NAPOCA;2019-01-31 02:00:49;;;;0;false;;Basic - Food;1000;false
        """.trimIndent()
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = listOf(AccountMatcher("ING old", "ING"))
        )

        val importTransactions = walletCsvImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionId).isNotNull()
        assertThat(importTransactions[0].date.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountName).isEqualTo("ING")
        assertThat(importTransactions[0].records[0].currency).isEqualTo("RON")
        assertThat(importTransactions[0].records[0].amount).isEqualTo("-13.80".toBigDecimal())
    }

    @Test
    fun `should parse transfer wallet csv import item`() {
        val fileContent = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
            ING old;TRANSFER;RON;-400.00;-400.00;Expenses;TRANSFER;Bank transfer;Retragere numerar 5526 ING OFFICE CJ MARAST RO CLUJ;2019-01-29 02:00:37;;;;0;true;;;20001;false
            Cash RON;TRANSFER;RON;400.00;400.00;Income;TRANSFER;Bank transfer;Retragere numerar 5526 ING OFFICE CJ MARAST RO CLUJ;2019-01-29 02:00:37;;;;0;true;;;20001;false
    """.trimIndent()
        val importConfiguration = ImportConfiguration(
            importType = ImportType.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcher("ING old", "ING"),
                AccountMatcher("Cash RON", "Cash")
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
}
