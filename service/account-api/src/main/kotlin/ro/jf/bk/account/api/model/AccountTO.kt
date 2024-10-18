package ro.jf.bk.account.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class AccountTO {
    abstract val id: UUID
    // TODO(Johann) could be interesting to treat account names and fund names as value classes for less confusion
    abstract val name: String

    @Serializable
    @SerialName("currency")
    data class Currency(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        override val name: String,
        val currency: String
    ) : AccountTO()

    @Serializable
    @SerialName("instrument")
    data class Instrument(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        override val name: String,
        val currency: String,
        val symbol: String
    ) : AccountTO()
}
