package ro.jf.funds.commons.persistence

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan

suspend fun <T> blockingTransaction(statement: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = {
        withSuspendingSpan { statement() }
    })
