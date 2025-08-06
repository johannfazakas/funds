package ro.jf.funds.importer.service.service.parser

import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.api.model.*
import java.math.BigDecimal

class FundsFormatImportParserTest {
    private val fundsFormatImportParser = FundsFormatImportParser(CsvParser())

    @Test
    fun `should parse investment transactions`() {
        val fileContent = generateFileContent(
            FundsFormatCsvRowContent(
                "2022-04-04", "BT EUR", "-2970.0", "RON", "currency", "transfer XTB 600 EUR", "investment"
            ),
            FundsFormatCsvRowContent(
                "2022-04-04", "XTB EUR", "600.0", "EUR", "currency", "transfer XTB 600 EUR", "investment"
            ),
            FundsFormatCsvRowContent(
                "2022-04-05", "XTB EUR", "-544.25", "EUR", "currency", "buy 7 x EUNL", ""
            ),
            FundsFormatCsvRowContent(
                "2022-04-05", "XTB EUNL", "7", "EUNL", "symbol", "buy 7 x EUNL", ""
            ),
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.FUNDS_FORMAT,
            accountMatchers = listOf(
                AccountMatcherTO.ByName("BT EUR", AccountName("BT EUR")),
                AccountMatcherTO.ByName("XTB EUR", AccountName("XTB EUR")),
                AccountMatcherTO.ByName("XTB EUNL", AccountName("XTB EUNL")),
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccount("BT EUR", FundName("Expenses")),
                FundMatcherTO.ByAccount("XTB EUR", FundName("Investments")),
                FundMatcherTO.ByAccount("XTB EUNL", FundName("Investments")),
            ),
            labelMatchers = listOf(
                LabelMatcherTO("investment", Label("investment")),
            )
        )

        val importTransactions = fundsFormatImportParser.parse(importConfiguration, listOf(fileContent))

        assertThat(importTransactions).hasSize(1)
        val transfer = importTransactions[0]
        assertThat(transfer.transactionExternalId).isNotNull
        assertThat(transfer.dateTime).isEqualTo(LocalDateTime.parse("2022-04-04T00:00:00"))
        assertThat(transfer.records).hasSize(2)
        assertThat(transfer.records[0].accountName).isEqualTo(AccountName("BT EUR"))
        assertThat(transfer.records[0].fundName).isEqualTo(FundName("Expenses"))
        assertThat(transfer.records[0].unit).isEqualTo(Currency.RON)
        assertThat(transfer.records[0].amount).isEqualByComparingTo(BigDecimal("-2970.0"))
        assertThat(transfer.records[0].labels).containsExactly(Label("investment"))
        assertThat(transfer.records[1].accountName).isEqualTo(AccountName("XTB EUR"))
        assertThat(transfer.records[1].fundName).isEqualTo(FundName("Investments"))
        assertThat(transfer.records[1].unit).isEqualTo(Currency.EUR)
        assertThat(transfer.records[1].amount).isEqualByComparingTo(BigDecimal("600.0"))
        assertThat(transfer.records[1].labels).containsExactly(Label("investment"))

        // TODO(Johann-41) enable test
//        val investment = importTransactions[1]
//        assertThat(investment.transactionExternalId).isNotNull
//        assertThat(investment.dateTime).isEqualTo(LocalDateTime.parse("2022-04-05T00:00:00"))
//        assertThat(investment.records).hasSize(2)
//        assertThat(investment.records[0].accountName).isEqualTo(AccountName("XTB EUR"))
//        assertThat(investment.records[0].fundName).isEqualTo(FundName("Investments"))
//        assertThat(investment.records[0].unit).isEqualTo(Currency.EUR)
//        assertThat(investment.records[0].amount).isEqualByComparingTo(BigDecimal("-544.25"))
//        assertThat(investment.records[0].labels).isEmpty()
//        assertThat(investment.records[1].accountName).isEqualTo(AccountName("XTB EUNL"))
//        assertThat(investment.records[1].fundName).isEqualTo(FundName("Investments"))
//        assertThat(investment.records[1].unit).isEqualTo(Symbol("EUNL"))
//        assertThat(investment.records[1].amount).isEqualByComparingTo(BigDecimal("7.0"))
//        assertThat(investment.records[1].labels).isEmpty()
    }

    data class FundsFormatCsvRowContent(
        val date: String,
        val account: String,
        val amount: String,
        val unit: String,
        val unitType: String,
        val note: String,
        val label: String,
    )

    private fun generateFileContent(vararg rowContent: FundsFormatCsvRowContent): String {
        val header = """
            date;account;amount;unit;unit_type;note;label
        """.trimIndent()
        val rows = rowContent.joinToString("\n") { (date, account, amount, unit, unitType, note, label) ->
            "$date;$account;$amount;$unit;$unitType;$note;$label"
        }
        return "$header\n$rows"
    }
}