package ro.jf.funds.importer.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal

data class ImportParsedTransaction(
    val transactionExternalId: String,
    val dateTime: LocalDateTime,
    val records: List<ImportParsedRecord>,
) {
    init {
        if (records.size !in 1..3) {
            throw ImportDataException("ImportTransaction must have 1 to 3 records: $this.")
        }
    }
}

data class ImportParsedRecord(
    val accountName: AccountName,
    val fundName: FundName,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val labels: List<Label> = emptyList(),
)
