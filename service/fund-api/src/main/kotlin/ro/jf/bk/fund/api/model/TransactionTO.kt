package ro.jf.bk.fund.api.model

import kotlinx.datetime.LocalDateTime

data class TransactionTO(
    val id: String,
    val userId: String,
    val dateTime: LocalDateTime,
    val records: List<RecordTO>,
)
