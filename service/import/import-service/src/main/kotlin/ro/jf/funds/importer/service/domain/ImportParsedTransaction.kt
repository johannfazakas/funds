package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.platform.api.model.FinancialUnit

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
    val accountId: Uuid,
    val fundId: Uuid,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val categoryId: Uuid? = null,
    val note: String? = null,
)
