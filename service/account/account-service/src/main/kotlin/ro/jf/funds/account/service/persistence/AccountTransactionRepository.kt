package ro.jf.funds.account.service.persistence

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.domain.AccountRecord
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.persistence.AccountRepository.AccountTable
import ro.jf.funds.commons.service.persistence.blockingTransaction
import ro.jf.funds.commons.service.persistence.jsonb
import java.util.*

class AccountTransactionRepository(
    private val database: Database
) {
    object AccountTransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val dateTime = datetime("date_time")
        val metadata = jsonb(name = "metadata")
    }

    object AccountRecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val accountId = uuid("account_id").references(AccountTable.id)
        val amount = decimal("amount", 20, 8)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
        val metadata = jsonb(name = "metadata")
    }

    suspend fun list(userId: UUID): List<AccountTransaction> = blockingTransaction {
        (AccountTransactionTable leftJoin AccountRecordTable)
            .select { AccountTransactionTable.userId eq userId }
            .groupBy { it[AccountTransactionTable.id].value }
            .map { (_, rows) -> toTransaction(rows) }
    }

    suspend fun findById(userId: UUID, transactionId: UUID): AccountTransaction? = blockingTransaction {
        (AccountTransactionTable leftJoin AccountRecordTable)
            .select { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
            .groupBy { it[AccountTransactionTable.id].value }
            .map { (_, rows) -> toTransaction(rows) }
            .firstOrNull()
    }

    suspend fun save(userId: UUID, command: CreateAccountTransactionTO): AccountTransaction = blockingTransaction {
        saveTransaction(userId, command)
    }

    suspend fun saveAll(
        userId: UUID,
        requests: CreateAccountTransactionsTO
    ): List<AccountTransaction> = blockingTransaction {
        requests.transactions.map { saveTransaction(userId, it) }
    }

    private fun saveTransaction(
        userId: UUID,
        command: CreateAccountTransactionTO
    ): AccountTransaction {
        val transaction = AccountTransactionTable.insert {
            it[AccountTransactionTable.userId] = userId
            it[dateTime] = command.dateTime.toJavaLocalDateTime()
            it[metadata] = command.metadata
        }
        val transactionId = transaction[AccountTransactionTable.id].value
        val records = command.records.map { record ->
            AccountRecordTable.insert {
                it[AccountRecordTable.userId] = userId
                it[AccountRecordTable.transactionId] = transactionId
                it[accountId] = record.accountId
                it[amount] = record.amount
                it[unit] = record.unit.value
                it[unitType] = record.unit.toUnitType()
                it[metadata] = record.metadata
            }
        }
        return AccountTransaction(
            id = transactionId,
            userId = transaction[AccountTransactionTable.userId],
            dateTime = transaction[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = records.map {
                AccountRecord(
                    id = it[AccountRecordTable.id].value,
                    accountId = it[AccountRecordTable.accountId],
                    amount = it[AccountRecordTable.amount],
                    unit =  toFinancialUnit(it[AccountRecordTable.unitType], it[AccountRecordTable.unit]),
                    metadata = it[AccountRecordTable.metadata]
                )
            },
            metadata = transaction[AccountTransactionTable.metadata]
        )
    }

    suspend fun deleteByUserId(userId: UUID): Unit = blockingTransaction {
        AccountRecordTable.deleteWhere { AccountRecordTable.userId eq userId }
        AccountTransactionTable.deleteWhere { AccountTransactionTable.userId eq userId }
    }

    suspend fun deleteById(userId: UUID, transactionId: UUID): Unit = blockingTransaction {
        AccountRecordTable.deleteWhere { AccountRecordTable.userId eq userId and (AccountRecordTable.transactionId eq transactionId) }
        AccountTransactionTable.deleteWhere { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        AccountRecordTable.deleteAll()
        AccountTransactionTable.deleteAll()
    }

    private fun toTransaction(
        rows: List<ResultRow>
    ): AccountTransaction {
        val accountRecords = rows.map {
            AccountRecord(
                id = it[AccountRecordTable.id].value,
                accountId = it[AccountRecordTable.accountId],
                amount = it[AccountRecordTable.amount],
                unit = toFinancialUnit(it[AccountRecordTable.unitType], it[AccountRecordTable.unit]),
                metadata = it[AccountRecordTable.metadata]
            )
        }
        return AccountTransaction(
            id = rows.first()[AccountTransactionTable.id].value,
            userId = rows.first()[AccountTransactionTable.userId],
            dateTime = rows.first()[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = accountRecords,
            metadata = rows.first()[AccountTransactionTable.metadata]
        )
    }
}
