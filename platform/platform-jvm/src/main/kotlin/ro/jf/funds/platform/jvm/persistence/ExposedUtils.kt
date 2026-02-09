package ro.jf.funds.platform.jvm.persistence

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import ro.jf.funds.platform.api.model.SortOrder
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import java.math.BigDecimal as JavaBigDecimal
import org.jetbrains.exposed.sql.SortOrder as ExposedSortOrder

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

fun Table.bigDecimal(name: String, precision: Int, scale: Int): Column<BigDecimal> =
    decimal(name, precision, scale).transform(
        wrap = { BigDecimal.parseString(it.toPlainString()) },
        unwrap = { JavaBigDecimal(it.toStringExpanded()) }
    )

fun SortOrder.toExposedSortOrder(): ExposedSortOrder = when (this) {
    SortOrder.ASC -> ExposedSortOrder.ASC
    SortOrder.DESC -> ExposedSortOrder.DESC
}
