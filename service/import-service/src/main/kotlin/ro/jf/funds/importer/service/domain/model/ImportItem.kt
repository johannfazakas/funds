package ro.jf.funds.importer.service.domain.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class ImportItem(
    val amount: BigDecimal,
    // TODO(Johann) extract Currency type? maybe to a library?
    val currency: String,
    val accountName: String,
    val date: LocalDateTime,
    val transactionId: String
)
