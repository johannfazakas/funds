package ro.jf.funds.importer.service.service.parser

import com.benasher44.uuid.uuid4
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import com.ionspin.kotlin.bignum.decimal.BigDecimal

class WalletCsvImportParserTest {
    private val walletCsvImportParser = WalletCsvImportParser(CsvParser())

    private val ingAccountId = uuid4()
    private val cashAccountId = uuid4()
    private val cashEurAccountId = uuid4()
    private val expensesFundId = uuid4()
    private val incomeFundId = uuid4()
    private val savingsFundId = uuid4()
    private val giftFundId = uuid4()
    private val workFundId = uuid4()
    private val basicCategoryId = uuid4()
    private val exchangeCategoryId = uuid4()
    private val giftsCategoryId = uuid4()
    private val workCategoryId = uuid4()

    @Test
    fun `given simple item - when parsing - then returns single parsed transaction`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            categoryMatchers = listOf(CategoryMatcher(listOf("Basic - Food"), basicCategoryId)),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-31T02:00:49")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[0].records[0].fundId).isEqualTo(expensesFundId)
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("-13.80"))
        assertThat(importTransactions[0].records[0].categoryId).isEqualTo(basicCategoryId)
    }

    @Test
    fun `given transfer items - when parsing - then returns transfer transaction`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Cash RON", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher(listOf("ING old"), ingAccountId),
                AccountMatcher(listOf("Cash RON"), cashAccountId)
            ),
            fundMatchers = listOf(
                FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId),
                FundMatcher(listOf(cashAccountId), defaultFundId = expensesFundId),
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
        assertThat(importTransactions[0].records[0].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("-400.00"))
        assertThat(importTransactions[0].records[1].accountId).isEqualTo(cashAccountId)
        assertThat(importTransactions[0].records[1].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[1].amount).isEqualTo(BigDecimal.parseString("400.00"))
    }

    @Test
    fun `given exchange items - when parsing - then returns exchange transaction`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("Euro", "EUR", "-1.89", "Exchange", "2019-04-23 21:45:02", "exchange"),
            WalletCsvRowContent("Cash RON", "RON", "-1434.00", "Exchange", "2019-04-23 21:45:49", "exchange"),
            WalletCsvRowContent("Euro", "EUR", "301.24", "Exchange", "2019-04-23 21:45:49", "exchange"),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher(listOf("Euro"), cashEurAccountId),
                AccountMatcher(listOf("Cash RON"), cashAccountId)
            ),
            fundMatchers = listOf(
                FundMatcher(listOf(cashEurAccountId), defaultFundId = expensesFundId),
                FundMatcher(listOf(cashAccountId), defaultFundId = expensesFundId),
            ),
            exchangeMatchers = listOf(ExchangeMatcher.ByLabel("Exchange")),
            categoryMatchers = listOf(CategoryMatcher(listOf("Exchange"), exchangeCategoryId)),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
        assertThat(importTransactions).hasSize(1)
        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-04-23T21:45:02")
        assertThat(importTransactions[0].records).hasSize(3)
        assertThat(importTransactions[0].records).containsExactlyInAnyOrder(
            ImportParsedRecord(cashEurAccountId, expensesFundId, Currency.EUR, BigDecimal.parseString("-1.89"), exchangeCategoryId, "exchange"),
            ImportParsedRecord(cashAccountId, expensesFundId, Currency.RON, BigDecimal.parseString("-1434.00"), exchangeCategoryId, "exchange"),
            ImportParsedRecord(cashEurAccountId, expensesFundId, Currency.EUR, BigDecimal.parseString("301.24"), exchangeCategoryId, "exchange"),
        )
    }

    @Test
    fun `given item with intermediary fund - when parsing - then returns main and transfer transactions`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "740.00", "Gift income", "2019-01-06 02:00:23")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(
                FundMatcher(
                    listOf(ingAccountId),
                    defaultFundId = expensesFundId,
                    categoryRules = listOf(
                        FundMatcher.CategoryRule(giftsCategoryId, expensesFundId, intermediaryFundId = giftFundId)
                    ),
                ),
            ),
            exchangeMatchers = emptyList(),
            categoryMatchers = listOf(CategoryMatcher(listOf("Gift income"), giftsCategoryId)),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
        assertThat(importTransactions).hasSize(2)

        assertThat(importTransactions[0].transactionExternalId).isNotNull()
        assertThat(importTransactions[0].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[0].records[0].fundId).isEqualTo(giftFundId)
        assertThat(importTransactions[0].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[0].records[0].amount).isEqualTo(BigDecimal.parseString("740.00"))

        assertThat(importTransactions[1].transactionExternalId).endsWith("-fund-transfer")
        assertThat(importTransactions[1].dateTime.toString()).isEqualTo("2019-01-06T02:00:23")
        assertThat(importTransactions[1].records).hasSize(2)
        assertThat(importTransactions[1].records[0].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[1].records[0].fundId).isEqualTo(giftFundId)
        assertThat(importTransactions[1].records[0].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[0].amount).isEqualTo(BigDecimal.parseString("-740.00"))

        assertThat(importTransactions[1].records[1].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[1].records[1].fundId).isEqualTo(expensesFundId)
        assertThat(importTransactions[1].records[1].unit).isEqualTo(Currency.RON)
        assertThat(importTransactions[1].records[1].amount).isEqualTo(BigDecimal.parseString("740.00"))
    }

    @Test
    fun `given item with account-specific category rule - when parsing - then uses category rule fund`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "6740.00", "Work Income", "2019-01-06 02:00:23")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(
                FundMatcher(
                    listOf(ingAccountId),
                    defaultFundId = expensesFundId,
                    categoryRules = listOf(
                        FundMatcher.CategoryRule(workCategoryId, expensesFundId, intermediaryFundId = workFundId)
                    ),
                ),
            ),
            exchangeMatchers = emptyList(),
            categoryMatchers = listOf(CategoryMatcher(listOf("Work Income"), workCategoryId)),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.failures()).isEmpty()
        val importTransactions = results.successes()
        assertThat(importTransactions).hasSize(2)

        assertThat(importTransactions[0].records).hasSize(1)
        assertThat(importTransactions[0].records[0].accountId).isEqualTo(ingAccountId)
        assertThat(importTransactions[0].records[0].fundId).isEqualTo(workFundId)

        assertThat(importTransactions[1].records).hasSize(2)
        assertThat(importTransactions[1].records[0].fundId).isEqualTo(workFundId)
        assertThat(importTransactions[1].records[0].amount).isEqualTo(BigDecimal.parseString("-6740.00"))
        assertThat(importTransactions[1].records[1].fundId).isEqualTo(expensesFundId)
        assertThat(importTransactions[1].records[1].amount).isEqualTo(BigDecimal.parseString("6740.00"))
    }

    @Test
    fun `given unmatched account name - when parsing - then returns error`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING new"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems.first()).contains("Account name not matched: 'ING old'")
    }

    @Test
    fun `given empty import - when parsing - then throws import data exception`() {
        val fileContent = """
            account;category;currency;amount;ref_currency_amount;type;payment_type;payment_type_local;note;date;gps_latitude;gps_longitude;gps_accuracy_in_meters;warranty_in_month;transfer;payee;labels;envelope_id;custom_category
        """.trimIndent()
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            exchangeMatchers = emptyList(),
        )

        assertThatThrownBy { walletCsvImportParser.parse(matchers, fileContent) }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("No import reportdata")
    }

    @Test
    fun `given skipped account in transaction - when parsing - then skips entire transaction`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-400.00", "", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Skipped account", "RON", "400.00", "", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(
                AccountMatcher(listOf("ING old"), ingAccountId),
                AccountMatcher(listOf("Skipped account"), skipped = true)
            ),
            fundMatchers = listOf(
                FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId),
            ),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        assertThat(results.failures()).isEmpty()
    }

    @Test
    fun `given multiple unmatched accounts - when parsing - then returns all errors`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("Unknown1", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Unknown2", "RON", "-25.00", "Basic - Food", "2019-01-31 03:00:00"),
            WalletCsvRowContent("Unknown1", "RON", "-10.00", "Basic - Food", "2019-01-31 04:00:00"),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        val problems = results.failures().flatMap { it.problems }.toSet()
        assertThat(problems).hasSize(2)
        assertThat(problems).anyMatch { it.contains("Account name not matched: 'Unknown1'") }
        assertThat(problems).anyMatch { it.contains("Account name not matched: 'Unknown2'") }
    }

    @Test
    fun `given mix of valid and invalid items - when parsing - then returns both`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Basic - Food", "2019-01-31 02:00:49"),
            WalletCsvRowContent("Unknown", "RON", "-25.00", "Basic - Food", "2019-01-31 03:00:00"),
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            categoryMatchers = listOf(CategoryMatcher(listOf("Basic - Food"), basicCategoryId)),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        val transactions = results.successes()
        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].records[0].accountId).isEqualTo(ingAccountId)
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems.first()).contains("Account name not matched: 'Unknown'")
    }

    @Test
    fun `given no matching category - when parsing - then returns error`() {
        val fileContent = generateFileContent(
            WalletCsvRowContent("ING old", "RON", "-13.80", "Unknown Label", "2019-01-31 02:00:49")
        )
        val matchers = ImportMatchers(
            accountMatchers = listOf(AccountMatcher(listOf("ING old"), ingAccountId)),
            fundMatchers = listOf(FundMatcher(listOf(ingAccountId), defaultFundId = expensesFundId)),
            exchangeMatchers = emptyList(),
        )

        val results = walletCsvImportParser.parse(matchers, fileContent)

        assertThat(results.successes()).isEmpty()
        val errors = results.failures()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].problems.first()).contains("No category matcher found")
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
