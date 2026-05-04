package ro.jf.funds.fund.service.domain

import kotlinx.datetime.LocalDate
import java.util.UUID

data class RecordFilter(
    val accountId: UUID? = null,
    val fundId: UUID? = null,
    val unit: String? = null,
    val categoryId: UUID? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
)
