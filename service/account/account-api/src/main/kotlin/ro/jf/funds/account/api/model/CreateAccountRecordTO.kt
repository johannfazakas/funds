package ro.jf.funds.account.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class CreateAccountRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val metadata: Map<String, String> = emptyMap(),
)