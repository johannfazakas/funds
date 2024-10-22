package ro.jf.funds.account.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class AccountTO {
    abstract val id: UUID
    abstract val name: AccountName

    @Serializable
    @SerialName("currency")
    data class Currency(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        override val name: AccountName,
        val currency: String
    ) : AccountTO()

    @Serializable
    @SerialName("instrument")
    data class Instrument(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        override val name: AccountName,
        val currency: String,
        val symbol: String
    ) : AccountTO()
}
