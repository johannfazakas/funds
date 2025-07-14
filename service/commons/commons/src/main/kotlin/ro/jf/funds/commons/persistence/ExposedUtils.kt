package ro.jf.funds.commons.persistence

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan

suspend fun <T> blockingTransaction(statement: suspend Transaction.() -> T): T {
    return withSuspendingSpan(
        className = statement.javaClass.enclosingClass.name,
        methodName = statement.javaClass.enclosingMethod.name,
        attributes = emptyMap()
    ) {
        newSuspendedTransaction(Dispatchers.IO, statement = {
            statement()
        })
    }
}
