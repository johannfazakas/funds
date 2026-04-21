package ro.jf.funds.fund.service.domain

import java.util.*

data class Category(
    val id: UUID,
    val userId: UUID,
    val name: String,
)
