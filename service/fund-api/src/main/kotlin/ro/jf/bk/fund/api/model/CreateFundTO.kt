package ro.jf.bk.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateFundTO(
    val name: String,
    // TODO(Johann) accounts might not be needed at all, linking might be done only at record level
    val accounts: List<CreateFundAccountTO> = emptyList(),
)

@Serializable
data class CreateFundAccountTO(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
)
