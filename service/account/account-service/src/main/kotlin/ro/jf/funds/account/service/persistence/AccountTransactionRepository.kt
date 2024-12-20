package ro.jf.funds.account.service.persistence

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.api.model.TransactionsFilterTO
import ro.jf.funds.account.service.domain.AccountRecord
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.domain.Property
import ro.jf.funds.account.service.persistence.AccountRepository.AccountTable
import ro.jf.funds.commons.service.persistence.blockingTransaction
import java.util.*

class AccountTransactionRepository(
    private val database: Database,
) {
    object AccountTransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val dateTime = datetime("date_time")
    }

    object AccountRecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val accountId = uuid("account_id").references(AccountTable.id)
        val amount = decimal("amount", 20, 8)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
    }

    object TransactionPropertyTable : UUIDTable("transaction_property") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val key = varchar("key", 50)
        val value = varchar("value", 50)
        val type = varchar("type", 50)
    }

    object RecordPropertyTable : UUIDTable("record_property") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val recordId = uuid("record_id").references(AccountRecordTable.id)
        val key = varchar("key", 50)
        val value = varchar("value", 50)
    }

    enum class TransactionPropertyType(val key: String) {
        TRANSACTION("transaction"),
        RECORD("record"),
    }

    suspend fun list(
        userId: UUID,
        filter: TransactionsFilterTO,
    ): List<AccountTransaction> = blockingTransaction {
        val transactionIds = findTransactionIdsByProperties(userId, filter)
        (AccountTransactionTable
                leftJoin AccountRecordTable
                leftJoin TransactionPropertyTable
                leftJoin RecordPropertyTable)
            .select {
                transactionIds
                    ?.let { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id inList transactionIds) }
                    ?: (AccountTransactionTable.userId eq userId)
            }
            .toTransactions()
    }

    private fun findTransactionIdsByProperties(userId: UUID, filter: TransactionsFilterTO): List<UUID>? {
        if (filter.transactionProperties.isEmpty() && filter.recordProperties.isEmpty()) {
            return null
        }
        val transactionPropertiesFilter = filter.transactionProperties
            .flatMap { (key, values) -> values.map { key to it } }
            .map { (key, value) ->
                TransactionPropertyTable.userId eq userId and
                        (TransactionPropertyTable.key eq key) and
                        (TransactionPropertyTable.value eq value) and
                        (TransactionPropertyTable.type eq TransactionPropertyType.TRANSACTION.key)
            }
            .reduceOrNull { acc, op -> acc or op }
            ?.let {
                TransactionPropertyTable.select { it }
                    .map {
                        it[TransactionPropertyTable.transactionId] to Property(
                            it[TransactionPropertyTable.id].value,
                            it[TransactionPropertyTable.key],
                            it[TransactionPropertyTable.value]
                        )
                    }
                    .groupBy({ it.first }) { it.second }
            }
            ?: emptyMap()


        val recordPropertiesFilter = filter.recordProperties
            .flatMap { (key, values) -> values.map { key to it } }
            .map { (key, value) ->
                RecordPropertyTable.userId eq userId and
                        (RecordPropertyTable.key eq key) and
                        (RecordPropertyTable.value eq value)
            }
            .reduceOrNull { acc, op -> acc or op }
            ?.let {
                RecordPropertyTable.select { it }
                    .map {
                        it[RecordPropertyTable.transactionId] to Property(
                            it[RecordPropertyTable.id].value,
                            it[RecordPropertyTable.key],
                            it[RecordPropertyTable.value]
                        )
                    }
                    .groupBy({ it.first }) { it.second }
            }
            ?: emptyMap()

        val transactionsMatchingAllProperties = (transactionPropertiesFilter + recordPropertiesFilter)
            .filter { (_, properties) ->
                filter.recordProperties
                    .flatMap { (key, values) -> values.map { key to it } }
                    .all { (key, value) -> properties.any { it.key == key && it.value == value } }
                        &&
                        filter.transactionProperties
                            .flatMap { (key, values) -> values.map { key to it } }
                            .all { (key, value) -> properties.any { it.key == key && it.value == value } }
            }
            .map { it.key }
        return transactionsMatchingAllProperties
    }

    suspend fun findById(userId: UUID, transactionId: UUID): AccountTransaction? = blockingTransaction {
        (AccountTransactionTable
                leftJoin AccountRecordTable
                leftJoin TransactionPropertyTable
                leftJoin RecordPropertyTable)
            .select { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
            .groupBy { it[AccountTransactionTable.id].value }
            .map { (_, rows) -> rows.toTransaction() }
            .firstOrNull()
    }

    suspend fun save(userId: UUID, command: CreateAccountTransactionTO): AccountTransaction = blockingTransaction {
        saveTransaction(userId, command)
    }

    suspend fun saveAll(
        userId: UUID,
        requests: CreateAccountTransactionsTO,
    ): List<AccountTransaction> = blockingTransaction {
        requests.transactions.map { saveTransaction(userId, it) }
    }


    suspend fun deleteByUserId(userId: UUID): Unit = blockingTransaction {
        AccountRecordTable.deleteWhere { AccountRecordTable.userId eq userId }
        AccountTransactionTable.deleteWhere { AccountTransactionTable.userId eq userId }
    }

    suspend fun deleteById(userId: UUID, transactionId: UUID): Unit = blockingTransaction {
        RecordPropertyTable.deleteWhere { RecordPropertyTable.userId eq userId and (RecordPropertyTable.transactionId eq transactionId) }
        TransactionPropertyTable.deleteWhere { TransactionPropertyTable.userId eq userId and (TransactionPropertyTable.transactionId eq transactionId) }
        AccountRecordTable.deleteWhere { AccountRecordTable.userId eq userId and (AccountRecordTable.transactionId eq transactionId) }
        AccountTransactionTable.deleteWhere { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        RecordPropertyTable.deleteAll()
        TransactionPropertyTable.deleteAll()
        AccountRecordTable.deleteAll()
        AccountTransactionTable.deleteAll()
    }

    private fun saveTransaction(
        userId: UUID,
        command: CreateAccountTransactionTO,
    ): AccountTransaction {
        // TODO(Johann) Expenses by fund - refactor, regroup
        val transaction = insertTransaction(userId, command)
        val transactionId = transaction[AccountTransactionTable.id].value
        val transactionProperties = insertTransactionProperties(command, userId, transactionId)
            .map {
                Property(
                    it[TransactionPropertyTable.id].value,
                    it[TransactionPropertyTable.key],
                    it[TransactionPropertyTable.value]
                )
            }

        val transactionRecordProperties = insertTransactionRecordProperties(command, userId, transactionId)
        val records = command.records.map { record ->
            val insertedRecord = getInsertedRecord(userId, transactionId, record)
            val insertedProperties = record.properties.entries
                .flatMap { (key, values) -> values.map { value -> key to value } }
                .map { (key, value) ->
                    RecordPropertyTable.insert {
                        it[RecordPropertyTable.userId] = userId
                        it[RecordPropertyTable.transactionId] = transactionId
                        it[RecordPropertyTable.recordId] = insertedRecord[AccountRecordTable.id].value
                        it[RecordPropertyTable.key] = key
                        it[RecordPropertyTable.value] = value
                    }
                }
            insertedRecord.let {
                AccountRecord(
                    id = it[AccountRecordTable.id].value,
                    accountId = it[AccountRecordTable.accountId],
                    amount = it[AccountRecordTable.amount],
                    unit = toFinancialUnit(it[AccountRecordTable.unitType], it[AccountRecordTable.unit]),
                    properties = insertedProperties
                        .map { savedProperty ->
                            Property(
                                savedProperty[RecordPropertyTable.id].value,
                                savedProperty[RecordPropertyTable.key],
                                savedProperty[RecordPropertyTable.value]
                            )
                        }
                )
            }
        }
        return AccountTransaction(
            id = transactionId,
            userId = transaction[AccountTransactionTable.userId],
            dateTime = transaction[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = records,
            properties = transactionProperties + records.flatMap { it.properties }
        )
    }

    private fun transactionFilter(userId: UUID, filter: TransactionsFilterTO): Op<Boolean> {
        val userFilter = AccountTransactionTable.userId eq userId

        val transactionPropertiesFilter = filter.transactionProperties
            .flatMap { (key, values) -> values.map { key to it } }
            .map { (key, value) ->
                sequenceOf(
                    TransactionPropertyTable.userId eq userId,
                    TransactionPropertyTable.key eq key,
                    TransactionPropertyTable.value eq value,
                    TransactionPropertyTable.type eq TransactionPropertyType.TRANSACTION.key
                ).reduce { acc, op -> acc and op }
            }

        val recordPropertiesFilter = filter.recordProperties
            .flatMap { (key, values) -> values.map { key to it } }
            .map { (key, value) ->
                sequenceOf(
                    TransactionPropertyTable.userId eq userId,
                    TransactionPropertyTable.key eq key,
                    TransactionPropertyTable.value eq value,
                    TransactionPropertyTable.type eq TransactionPropertyType.RECORD.key
                ).reduce { acc, op -> acc and op }
            }

        val propertiesFilter = sequenceOf(transactionPropertiesFilter, recordPropertiesFilter)
            .flatten()
            .reduceOrNull { acc, op -> acc or op }
        return listOfNotNull(userFilter, propertiesFilter)
            .reduceOrNull { acc, op -> acc and op } ?: userFilter
    }

    private fun getInsertedRecord(
        userId: UUID,
        transactionId: UUID,
        record: CreateAccountRecordTO,
    ) = AccountRecordTable.insert {
        it[AccountRecordTable.userId] = userId
        it[AccountRecordTable.transactionId] = transactionId
        it[accountId] = record.accountId
        it[amount] = record.amount
        it[unit] = record.unit.value
        it[unitType] = record.unit.toUnitType()
    }

    private fun insertTransactionRecordProperties(
        command: CreateAccountTransactionTO,
        userId: UUID,
        transactionId: UUID,
    ) = command.records
        .flatMap { record -> record.properties.entries }
        .flatMap { (key, values) -> values.map { value -> key to value } }
        .map { (key, value) ->
            TransactionPropertyTable.insert {
                it[TransactionPropertyTable.userId] = userId
                it[TransactionPropertyTable.transactionId] = transactionId
                it[TransactionPropertyTable.key] = key
                it[TransactionPropertyTable.value] = value
                it[type] = TransactionPropertyType.RECORD.key
            }
        }

    private fun insertTransactionProperties(
        command: CreateAccountTransactionTO,
        userId: UUID,
        transactionId: UUID,
    ) = command.properties.entries
        .flatMap { (key, values) -> values.map { value -> key to value } }
        .map { (key, value) ->
            TransactionPropertyTable.insert {
                it[TransactionPropertyTable.userId] = userId
                it[TransactionPropertyTable.transactionId] = transactionId
                it[TransactionPropertyTable.key] = key
                it[TransactionPropertyTable.value] = value
                it[type] = TransactionPropertyType.TRANSACTION.key
            }
        }

    private fun insertTransaction(
        userId: UUID,
        command: CreateAccountTransactionTO,
    ) = AccountTransactionTable.insert {
        it[AccountTransactionTable.userId] = userId
        it[dateTime] = command.dateTime.toJavaLocalDateTime()
    }

    private fun Query.toTransactions(): List<AccountTransaction> = this
        .groupBy { it[AccountTransactionTable.id].value }
        .map { (_, rows) -> rows.toTransaction() }

    private fun List<ResultRow>.toTransaction(
    ): AccountTransaction {
        return AccountTransaction(
            id = this.first()[AccountTransactionTable.id].value,
            userId = this.first()[AccountTransactionTable.userId],
            dateTime = this.first()[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = toRecords(),
            properties = toTransactionProperties()
        )
    }

    private fun List<ResultRow>.toTransactionProperties(): List<Property> = this
        .groupBy { it.getOrNull(TransactionPropertyTable.id)?.value }
        .filter { it.key != null }
        .filter { it.value.first()[TransactionPropertyTable.type] == TransactionPropertyType.TRANSACTION.key }
        .map { (_, rows) ->
            Property(
                id = rows.first()[TransactionPropertyTable.id].value,
                key = rows.first()[TransactionPropertyTable.key],
                value = rows.first()[TransactionPropertyTable.value]
            )
        }

    private fun List<ResultRow>.toRecords(): List<AccountRecord> = this
        .groupBy { it[AccountRecordTable.id].value }
        .map { (_, rows) -> rows.toRecord() }

    private fun List<ResultRow>.toRecord(): AccountRecord =
        AccountRecord(
            id = this.first()[AccountRecordTable.id].value,
            accountId = this.first()[AccountRecordTable.accountId],
            amount = this.first()[AccountRecordTable.amount],
            unit = toFinancialUnit(this.first()[AccountRecordTable.unitType], this.first()[AccountRecordTable.unit]),
            properties = this.toRecordProperties()
        )

    private fun List<ResultRow>.toRecordProperties(): List<Property> = this
        .groupBy { it.getOrNull(RecordPropertyTable.id)?.value }
        .filter { it.key != null }
        .map { (_, rows) ->
            Property(
                id = rows.first()[RecordPropertyTable.id].value,
                key = rows.first()[RecordPropertyTable.key],
                value = rows.first()[RecordPropertyTable.value]
            )
        }
}
