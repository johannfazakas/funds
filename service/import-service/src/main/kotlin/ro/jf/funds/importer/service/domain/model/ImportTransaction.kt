package ro.jf.funds.importer.service.domain.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class ImportTransaction(
    // TODO(Johann) should this be called a hash? How will it be used downstream?
    val transactionId: String,
    val date: LocalDateTime,
    val records: List<ImportRecord>
) {
    init {
        require(records.size in 1..2) { "ImportTransaction must have one or two records." }
    }
}

data class ImportRecord(
    val accountName: String,
    // TODO(Johann) add fund here
    val currency: String,
    val amount: BigDecimal,
)