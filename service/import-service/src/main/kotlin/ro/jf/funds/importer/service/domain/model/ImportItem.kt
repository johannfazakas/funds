package ro.jf.funds.importer.service.domain.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class ImportItem(
    val transactionId: String,
    val amount: BigDecimal,
    val accountName: String,
    val date: LocalDateTime,
)
