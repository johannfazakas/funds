package ro.jf.bk.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateFundTO(
    val name: String,
    val accounts: List<CreateFundAccountTO> = emptyList(),
) {
}

@Serializable
data class CreateFundAccountTO(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
)
