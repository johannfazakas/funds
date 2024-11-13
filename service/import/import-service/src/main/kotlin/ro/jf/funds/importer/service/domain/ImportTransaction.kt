package ro.jf.funds.importer.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal

data class ImportTransaction(
    // TODO(Johann) should this be called a hash? How will it be used downstream? Will it be?
    val transactionId: String,
    val dateTime: LocalDateTime,
    val records: List<ImportRecord>
) {
    init {
        if (records.size !in 1..3) {
            throw ImportDataException("ImportTransaction must have 1 to 3 records: $this.")
        }
    }
}

data class ImportRecord(
    val accountName: AccountName,
    val fundName: FundName,
    val currency: String,
    val amount: BigDecimal,
)