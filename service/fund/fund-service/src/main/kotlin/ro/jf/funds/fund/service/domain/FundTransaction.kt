package ro.jf.funds.fund.service.domain

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.util.*

data class FundTransaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<FundRecord>
)

data class FundRecord(
    val id: UUID,
    val fundId: UUID,
    val accountId: UUID,
    val amount: BigDecimal
)