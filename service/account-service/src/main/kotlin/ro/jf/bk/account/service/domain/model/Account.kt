package ro.jf.bk.account.service.domain.model

import java.util.*

sealed class Account {
    abstract val id: UUID
    abstract val userId: UUID
    abstract val name: String

    data class Currency(
        override val id: UUID,
        override val userId: UUID,
        override val name: String,
        val currency: String,
    ) : Account()

    data class Instrument(
        override val id: UUID,
        override val userId: UUID,
        override val name: String,
        val currency: String,
        val symbol: String
    ) : Account()
}
