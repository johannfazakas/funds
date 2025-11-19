package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.commons.api.serialization.UuidSerializer

@Serializable
data class AccountTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: AccountName,
    val unit: FinancialUnit,
)
