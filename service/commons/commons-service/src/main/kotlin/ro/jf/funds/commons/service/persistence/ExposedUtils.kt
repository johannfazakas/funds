package ro.jf.funds.commons.service.persistence

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

// TODO(Johann) move to single commons lib. applicable to everything in commons-service.
suspend fun <T> blockingTransaction(statement: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = statement)
