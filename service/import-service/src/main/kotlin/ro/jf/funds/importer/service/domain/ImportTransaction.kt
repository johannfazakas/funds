package ro.jf.funds.importer.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.fund.api.model.FundName
import java.math.BigDecimal

data class ImportTransaction(
    // TODO(Johann) should this be called a hash? How will it be used downstream?
    val transactionId: String,
    val dateTime: LocalDateTime,
    val records: List<ImportRecord>
) {
    init {
        require(records.size in 1..3) { "ImportTransaction must have 1 to 3 records." }
    }
}

data class ImportRecord(
    val accountName: AccountName,
    val fundName: FundName,
    val currency: String,
    val amount: BigDecimal,
)