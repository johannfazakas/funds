package ro.jf.funds.fund.service.domain

import java.util.*

data class LabelDomain(
    val id: UUID,
    val userId: UUID,
    val name: String,
)
