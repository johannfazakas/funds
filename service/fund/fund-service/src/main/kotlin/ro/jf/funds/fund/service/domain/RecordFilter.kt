package ro.jf.funds.fund.service.domain

import java.util.UUID

data class RecordFilter(
    val accountId: UUID? = null,
    val fundId: UUID? = null,
)
