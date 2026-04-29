package ro.jf.funds.importer.service.service.parser

import com.benasher44.uuid.uuid4
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.importer.service.domain.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal

class FundsFormatImportParserTest {
    private val fundsFormatImportParser = FundsFormatImportParser(CsvParser())

    private val btEurAccountId = uuid4()
    private val xtbEurAccountId = uuid4()
    private val xtbEunlAccountId = uuid4()
    private val expensesFundId = uuid4()
    private val investmentsFundId = uuid4()
    private val investmentCategoryId = uuid4()

    @Test
    fun `given investment transactions - when parsing - then returns parsed transactions`() {
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
                "2022-04-05", "XTB EUNL", "7", "EUNL", "instrument", "buy 7 x EUNL", ""
            ),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher(listOf("BT EUR"), btEurAccountId),
                AccountMatcher(listOf("XTB EUR"), xtbEurAccountId),
                AccountMatcher(listOf("XTB EUNL"), xtbEunlAccountId),
            ),
            fundMatchers = listOf(
                FundMatcher(listOf(btEurAccountId), defaultFundId = expensesFundId),
                FundMatcher(listOf(xtbEurAccountId, xtbEunlAccountId), defaultFundId = investmentsFundId),
            ),
            categoryMatchers = listOf(
                CategoryMatcher(listOf("investment"), investmentCategoryId),
            ),
        )

        val results = fundsFormatImportParser.parse(matchers, fileContent)

        assertThat(results.mapNotNull { it.exceptionOrNull() }).isEmpty()
        val importTransactions = results.mapNotNull { it.getOrNull() }
        assertThat(importTransactions).hasSize(2)
        val transfer = importTransactions[0]
        assertThat(transfer.transactionExternalId).isNotNull
        assertThat(transfer.dateTime).isEqualTo(LocalDateTime.parse("2022-04-04T00:00:00"))
        assertThat(transfer.records).hasSize(2)
        assertThat(transfer.records[0].accountId).isEqualTo(btEurAccountId)
        assertThat(transfer.records[0].fundId).isEqualTo(expensesFundId)
        assertThat(transfer.records[0].unit).isEqualTo(Currency.RON)
        assertThat(transfer.records[0].amount).isEqualByComparingTo(BigDecimal.parseString("-2970.0"))
        assertThat(transfer.records[0].categoryId).isEqualTo(investmentCategoryId)
        assertThat(transfer.records[1].accountId).isEqualTo(xtbEurAccountId)
        assertThat(transfer.records[1].fundId).isEqualTo(investmentsFundId)
        assertThat(transfer.records[1].unit).isEqualTo(Currency.EUR)
        assertThat(transfer.records[1].amount).isEqualByComparingTo(BigDecimal.parseString("600.0"))
        assertThat(transfer.records[1].categoryId).isEqualTo(investmentCategoryId)

        val investment = importTransactions[1]
        assertThat(investment.transactionExternalId).isNotNull
        assertThat(investment.dateTime).isEqualTo(LocalDateTime.parse("2022-04-05T00:00:00"))
        assertThat(investment.records).hasSize(2)
        assertThat(investment.records[0].accountId).isEqualTo(xtbEurAccountId)
        assertThat(investment.records[0].fundId).isEqualTo(investmentsFundId)
        assertThat(investment.records[0].unit).isEqualTo(Currency.EUR)
        assertThat(investment.records[0].amount).isEqualByComparingTo(BigDecimal.parseString("-544.25"))
        assertThat(investment.records[0].categoryId).isNull()
        assertThat(investment.records[1].accountId).isEqualTo(xtbEunlAccountId)
        assertThat(investment.records[1].fundId).isEqualTo(investmentsFundId)
        assertThat(investment.records[1].unit).isEqualTo(Instrument("EUNL"))
        assertThat(investment.records[1].amount).isEqualByComparingTo(BigDecimal.parseString("7.0"))
        assertThat(investment.records[1].categoryId).isNull()
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
