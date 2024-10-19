package ro.jf.bk.account.service.domain.model

import ro.jf.bk.account.api.model.AccountName
import java.util.*

sealed class Account {
    abstract val id: UUID
    abstract val userId: UUID
    abstract val name: AccountName

    data class Currency(
        override val id: UUID,
        override val userId: UUID,
        override val name: AccountName,
        val currency: String,
    ) : Account()

    data class Instrument(
        override val id: UUID,
        override val userId: UUID,
        override val name: AccountName,
        val currency: String,
        val symbol: String
    ) : Account()
}
