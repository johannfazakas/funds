package ro.jf.bk.account.service.adapter.persistence

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.bk.account.service.adapter.persistence.AccountExposedRepository.AccountTable
import ro.jf.bk.account.service.domain.command.CreateTransactionCommand
import ro.jf.bk.account.service.domain.model.Record
import ro.jf.bk.account.service.domain.model.Transaction
import ro.jf.bk.account.service.domain.port.TransactionRepository
import ro.jf.bk.commons.service.persistence.blockingTransaction
import ro.jf.bk.commons.service.persistence.jsonb
import java.util.*

class TransactionExposedRepository(
    private val database: Database
) : TransactionRepository {

    object TransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val dateTime = datetime("date_time")
        val metadata = jsonb(name = "metadata")
    }

    object RecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(TransactionTable.id)
        val accountId = uuid("account_id").references(AccountTable.id)
        val amount = decimal("amount", 20, 8)
        val metadata = jsonb(name = "metadata")
    }

    override suspend fun list(userId: UUID): List<Transaction> = blockingTransaction {
        (TransactionTable leftJoin RecordTable)
            .select { TransactionTable.userId eq userId }
            .groupBy { it[TransactionTable.id].value }
            .map { (_, rows) -> toTransaction(rows) }
    }

    override suspend fun findById(userId: UUID, transactionId: UUID): Transaction? = blockingTransaction {
        (TransactionTable leftJoin RecordTable)
            .select { TransactionTable.userId eq userId and (TransactionTable.id eq transactionId) }
            .groupBy { it[TransactionTable.id].value }
            .map { (_, rows) -> toTransaction(rows) }
            .firstOrNull()
    }

    override suspend fun save(command: CreateTransactionCommand): Transaction = blockingTransaction {
        val transaction = TransactionTable.insert {
            it[userId] = command.userId
            it[dateTime] = command.dateTime.toJavaLocalDateTime()
            it[metadata] = command.metadata
        }
        val transactionId = transaction[TransactionTable.id].value
        val records = command.records.map { record ->
            RecordTable.insert {
                it[userId] = command.userId
                it[this.transactionId] = transactionId
                it[accountId] = record.accountId
                it[amount] = record.amount
                it[metadata] = record.metadata
            }
        }
        Transaction(
            id = transactionId,
            userId = transaction[TransactionTable.userId],
            dateTime = transaction[TransactionTable.dateTime].toKotlinLocalDateTime(),
            records = records.map {
                Record(
                    id = it[RecordTable.id].value,
                    accountId = it[RecordTable.accountId],
                    amount = it[RecordTable.amount],
                    metadata = it[RecordTable.metadata]
                )
            },
            metadata = transaction[TransactionTable.metadata]
        )
    }

    override suspend fun deleteByUserId(userId: UUID): Unit = blockingTransaction {
        RecordTable.deleteWhere { RecordTable.userId eq userId }
        TransactionTable.deleteWhere { TransactionTable.userId eq userId }
    }

    override suspend fun deleteById(userId: UUID, transactionId: UUID): Unit = blockingTransaction {
        RecordTable.deleteWhere { RecordTable.userId eq userId and (RecordTable.transactionId eq transactionId) }
        TransactionTable.deleteWhere { TransactionTable.userId eq userId and (TransactionTable.id eq transactionId) }
    }

    override suspend fun deleteAll(): Unit = blockingTransaction {
        RecordTable.deleteAll()
        TransactionTable.deleteAll()
    }

    private fun toTransaction(
        rows: List<ResultRow>
    ): Transaction {
        val records = rows.map {
            Record(
                id = it[RecordTable.id].value,
                accountId = it[RecordTable.accountId],
                amount = it[RecordTable.amount],
                metadata = it[RecordTable.metadata]
            )
        }
        return Transaction(
            id = rows.first()[TransactionTable.id].value,
            userId = rows.first()[TransactionTable.userId],
            dateTime = rows.first()[TransactionTable.dateTime].toKotlinLocalDateTime(),
            records = records,
            metadata = rows.first()[TransactionTable.metadata]
        )
    }
}
