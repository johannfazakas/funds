package ro.jf.bk.fund.service.domain.fund

import java.util.*

data class Fund(
    val id: UUID,
    val userId: UUID,
    val name: String
)
