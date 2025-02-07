package ro.jf.funds.commons.persistence

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> blockingTransaction(statement: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = {
        addLogger(StdOutSqlLogger)
        statement()
    })
