package ro.jf.funds.account.service.persistence

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.service.domain.AccountRecord
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.domain.Property
import ro.jf.funds.account.service.persistence.AccountRepository.AccountTable
import ro.jf.funds.commons.model.asLabels
import ro.jf.funds.commons.model.asString
import ro.jf.funds.commons.model.toFinancialUnit
import ro.jf.funds.commons.persistence.blockingTransaction
import java.util.*
import java.util.UUID.randomUUID

class AccountTransactionRepository(
    private val database: Database,
) {
    object AccountTransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val externalId = varchar("external_id", 100)
        val dateTime = datetime("date_time")
    }

    object AccountRecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val accountId = uuid("account_id").references(AccountTable.id)
        val amount = decimal("amount", 20, 8)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
        val labels = varchar("labels", 100)
    }

    object TransactionPropertyTable : UUIDTable("transaction_property") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val key = varchar("key", 50)
        val value = varchar("value", 50)
    }

    object RecordPropertyTable : UUIDTable("record_property") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val recordId = uuid("record_id").references(AccountRecordTable.id)
        val key = varchar("key", 50)
        val value = varchar("value", 50)
    }

    suspend fun findById(userId: UUID, transactionId: UUID): AccountTransaction? = blockingTransaction {
        (AccountTransactionTable
                leftJoin AccountRecordTable
                leftJoin TransactionPropertyTable
                leftJoin RecordPropertyTable)
            .selectAll()
            .where { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
            .toTransactions()
            .singleOrNull()
    }

    suspend fun list(
        userId: UUID,
        filter: AccountTransactionFilterTO,
    ): List<AccountTransaction> = blockingTransaction {
        AccountTransactionTable
            .innerJoinWithMatchingTransactionProperties(userId, filter.transactionProperties)
            .innerJoinWithMatchingRecordProperties(userId, filter.recordProperties)
            .leftJoin(AccountRecordTable)
            .leftJoin(TransactionPropertyTable)
            .leftJoin(RecordPropertyTable)
            .selectAll()
            .where(toPredicate(userId, filter))
            .toTransactions()
    }

    suspend fun save(userId: UUID, command: CreateAccountTransactionTO): AccountTransaction = blockingTransaction {
        saveTransaction(userId, command)
    }

    suspend fun saveAll(
        userId: UUID, requests: CreateAccountTransactionsTO,
    ): List<AccountTransaction> = blockingTransaction {
        val storedTransactions =
            AccountTransactionTable.batchInsert(
                requests.transactions,
                ignore = true, // skipping potential transaction id clashes
                shouldReturnGeneratedValues = false
            ) { command: CreateAccountTransactionTO ->
                this[AccountTransactionTable.id] = randomUUID()
                this[AccountTransactionTable.userId] = userId
                this[AccountTransactionTable.externalId] = command.externalId
                this[AccountTransactionTable.dateTime] = command.dateTime.toJavaLocalDateTime()
            }
        val transactionRequestsByExternalId = requests.transactions.associateBy { it.externalId }
        val transactionIdsToTransactionRequests = storedTransactions
            .associate { it[AccountTransactionTable.id].value to transactionRequestsByExternalId[it[AccountTransactionTable.externalId]]!! }
        val propertyRequestsByTransactionId = transactionIdsToTransactionRequests
            .mapValues { (_, command) -> command.properties }
        val storedTransactionPropertiesByTransactionId = saveTransactionProperties(
            userId, propertyRequestsByTransactionId
        )
        val recordRequestsByTransactionId = transactionIdsToTransactionRequests
            .mapValues { (_, command) -> command.records }
        val storedRecordsByTransactionId = saveRecords(userId, recordRequestsByTransactionId)

        storedTransactions
            .map {
                val transactionId = it[AccountTransactionTable.id].value
                AccountTransaction(
                    id = transactionId,
                    userId = it[AccountTransactionTable.userId],
                    externalId = it[AccountTransactionTable.externalId],
                    dateTime = it[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
                    records = storedRecordsByTransactionId[transactionId] ?: emptyList(),
                    properties = storedTransactionPropertiesByTransactionId[transactionId] ?: emptyList()
                )
            }
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

    private fun ColumnSet.innerJoinWithMatchingTransactionProperties(
        userId: UUID,
        transactionProperties: List<PropertyTO>,
    ): ColumnSet {
        if (transactionProperties.isEmpty()) {
            return this
        }
        val transactionPropertiesMatcher = transactionProperties
            .map { (key, value) ->
                (TransactionPropertyTable.key eq key) and (TransactionPropertyTable.value eq value)
            }
            .reduce { acc, op -> acc or op }
        val matchingSubquery = TransactionPropertyTable
            .select(TransactionPropertyTable.transactionId)
            .where { TransactionPropertyTable.userId eq userId and transactionPropertiesMatcher }
            .groupBy(TransactionPropertyTable.transactionId)
            .having { TransactionPropertyTable.key.countDistinct() eq transactionProperties.size.toLong() }
            .alias("matchingTransactionProperties")
        return this.join(matchingSubquery, JoinType.INNER) {
            AccountTransactionTable.id eq matchingSubquery[TransactionPropertyTable.transactionId]
        }
    }

    private fun ColumnSet.innerJoinWithMatchingRecordProperties(
        userId: UUID,
        recordProperties: List<PropertyTO>,
    ): ColumnSet {
        if (recordProperties.isEmpty()) {
            return this
        }
        val recordPropertiesMatcher = recordProperties
            .map { (key, value) ->
                (RecordPropertyTable.key eq key) and (RecordPropertyTable.value eq value)
            }
            .reduce { acc, op -> acc or op }
        val matchingSubquery = RecordPropertyTable
            .select(RecordPropertyTable.transactionId)
            .where { RecordPropertyTable.userId eq userId and recordPropertiesMatcher }
            .groupBy(RecordPropertyTable.transactionId)
            .having { RecordPropertyTable.key.countDistinct() eq recordProperties.size.toLong() }
            .alias("matchingRecordProperties")
        return this.join(matchingSubquery, JoinType.INNER) {
            AccountTransactionTable.id eq matchingSubquery[RecordPropertyTable.transactionId]
        }
    }

    private fun saveTransaction(
        userId: UUID,
        command: CreateAccountTransactionTO,
    ): AccountTransaction {
        return insertTransaction(userId, command).let { transaction ->
            transaction.copy(
                records = command.records.map { recordRequest ->
                    insertRecord(userId, transaction.id, recordRequest).let { record ->
                        record.copy(
                            properties = insertRecordProperties(
                                userId, transaction.id, record.id, recordRequest.properties
                            )
                        )
                    }
                },
                properties = insertTransactionProperties(command, userId, transaction.id)
            )
        }
    }

    private fun insertTransaction(
        userId: UUID,
        command: CreateAccountTransactionTO,
    ) =
        AccountTransactionTable.insert {
            it[AccountTransactionTable.userId] = userId
            it[AccountTransactionTable.externalId] = command.externalId
            it[dateTime] = command.dateTime.toJavaLocalDateTime()
        }.let {
            AccountTransaction(
                id = it[AccountTransactionTable.id].value,
                userId = it[AccountTransactionTable.userId],
                externalId = it[AccountTransactionTable.externalId],
                dateTime = it[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            )
        }

    private fun saveRecords(
        userId: UUID,
        recordRequestsByTransactionId: Map<UUID, List<CreateAccountRecordTO>>,
    ): Map<UUID, List<AccountRecord>> {
        val transactionIdsToRecordRequest = recordRequestsByTransactionId
            .flatMap { (transactionId, records) ->
                records.map { record -> transactionId to record }
            }
        val storedRecords =
            AccountRecordTable.batchInsert(transactionIdsToRecordRequest, shouldReturnGeneratedValues = false) {
                this[AccountRecordTable.id] = randomUUID()
                this[AccountRecordTable.userId] = userId
                this[AccountRecordTable.transactionId] = it.first
                this[AccountRecordTable.accountId] = it.second.accountId
                this[AccountRecordTable.amount] = it.second.amount
                this[AccountRecordTable.unitType] = it.second.unit.type.value
                this[AccountRecordTable.unit] = it.second.unit.value
                this[AccountRecordTable.labels] = it.second.labels.asString()
            }

        val transactionIdsByRecordId = storedRecords
            .associate { it[AccountRecordTable.id].value to it[AccountRecordTable.transactionId] }
        val recordIdsToPropertyRequests = transactionIdsToRecordRequest
            .map { it.second }
            .zip(storedRecords)
            .associate { (recordRequest, storedRecord) ->
                storedRecord[AccountRecordTable.id].value to recordRequest.properties
            }
        val recordPropertiesByRecordId = saveRecordProperties(
            userId, recordIdsToPropertyRequests, transactionIdsByRecordId
        )

        return transactionIdsToRecordRequest
            .map { it.first }
            .zip(storedRecords)
            .map { (transactionId, storedRecord) ->
                transactionId to AccountRecord(
                    id = storedRecord[AccountRecordTable.id].value,
                    accountId = storedRecord[AccountRecordTable.accountId],
                    amount = storedRecord[AccountRecordTable.amount],
                    unit = toFinancialUnit(
                        storedRecord[AccountRecordTable.unitType],
                        storedRecord[AccountRecordTable.unit]
                    ),
                    labels = storedRecord[AccountRecordTable.labels].asLabels(),
                    properties = recordPropertiesByRecordId[storedRecord[AccountRecordTable.id].value] ?: emptyList()
                )
            }
            .groupBy({ it.first }) { it.second }
    }

    private fun saveTransactionProperties(
        userId: UUID,
        propertyRequestsByTransactionId: Map<UUID, List<PropertyTO>>,
    ): Map<UUID, List<Property>> {
        val transactionIdsToPropertyRequest = propertyRequestsByTransactionId
            .flatMap { (transactionId, properties) ->
                properties.map { transactionId to it }
            }
        val transactionPropertiesByTransactionId = TransactionPropertyTable
            .batchInsert(
                data = transactionIdsToPropertyRequest,
                shouldReturnGeneratedValues = false
            ) { (transactionId, property) ->
                this[TransactionPropertyTable.id] = randomUUID()
                this[TransactionPropertyTable.userId] = userId
                this[TransactionPropertyTable.transactionId] = transactionId
                this[TransactionPropertyTable.key] = property.key
                this[TransactionPropertyTable.value] = property.value
            }
            .groupBy { it[TransactionPropertyTable.transactionId] }
            .mapValues { (_, rows) ->
                rows.map {
                    Property(
                        id = it[TransactionPropertyTable.id].value,
                        key = it[TransactionPropertyTable.key],
                        value = it[TransactionPropertyTable.value]
                    )
                }
            }
        return transactionPropertiesByTransactionId
    }

    private fun saveRecordProperties(
        userId: UUID,
        propertyRequestsByRecordId: Map<UUID, List<PropertyTO>>,
        transactionIdByRecordId: Map<UUID, UUID>,
    ): Map<UUID, List<Property>> {
        val recordIdsToPropertyRequest = propertyRequestsByRecordId
            .flatMap { (transactionId, properties) ->
                properties.map { transactionId to it }
            }
        val storedTransactionProperties = RecordPropertyTable
            .batchInsert(
                data = recordIdsToPropertyRequest,
                shouldReturnGeneratedValues = false
            ) { (recordId, property) ->
                this[RecordPropertyTable.id] = randomUUID()
                this[RecordPropertyTable.userId] = userId
                this[RecordPropertyTable.transactionId] =
                    transactionIdByRecordId[recordId] ?: error("Transaction ID not found for record ID $recordId")
                this[RecordPropertyTable.recordId] = recordId
                this[RecordPropertyTable.key] = property.key
                this[RecordPropertyTable.value] = property.value
            }
        val transactionPropertiesByTransactionId = storedTransactionProperties
            .groupBy { it[RecordPropertyTable.recordId] }
            .mapValues { (_, rows) ->
                rows.map {
                    Property(
                        id = it[RecordPropertyTable.id].value,
                        key = it[RecordPropertyTable.key],
                        value = it[RecordPropertyTable.value]
                    )
                }
            }
        return transactionPropertiesByTransactionId
    }

    private fun insertRecord(
        userId: UUID,
        transactionId: UUID,
        record: CreateAccountRecordTO,
    ) =
        AccountRecordTable.insert {
            it[AccountRecordTable.userId] = userId
            it[AccountRecordTable.transactionId] = transactionId
            it[accountId] = record.accountId
            it[amount] = record.amount
            it[unit] = record.unit.value
            it[unitType] = record.unit.type.value
            it[labels] = record.labels.asString()
        }
            .let {
                AccountRecord(
                    id = it[AccountRecordTable.id].value,
                    accountId = it[AccountRecordTable.accountId],
                    amount = it[AccountRecordTable.amount],
                    unit = toFinancialUnit(it[AccountRecordTable.unitType], it[AccountRecordTable.unit]),
                    labels = it[AccountRecordTable.labels].asLabels(),
                )
            }

    private fun insertTransactionProperties(
        command: CreateAccountTransactionTO,
        userId: UUID,
        transactionId: UUID,
    ): List<Property> =
        command.properties
            .map { (key, value) ->
                TransactionPropertyTable.insert {
                    it[TransactionPropertyTable.userId] = userId
                    it[TransactionPropertyTable.transactionId] = transactionId
                    it[TransactionPropertyTable.key] = key
                    it[TransactionPropertyTable.value] = value
                }
            }
            .map {
                Property(
                    it[TransactionPropertyTable.id].value,
                    it[TransactionPropertyTable.key],
                    it[TransactionPropertyTable.value]
                )
            }

    private fun insertRecordProperties(
        userId: UUID,
        transactionId: UUID,
        recordId: UUID,
        properties: List<PropertyTO>,
    ): List<Property> =
        properties
            .map { (key, value) ->
                RecordPropertyTable.insert {
                    it[RecordPropertyTable.userId] = userId
                    it[RecordPropertyTable.transactionId] = transactionId
                    it[RecordPropertyTable.recordId] = recordId
                    it[RecordPropertyTable.key] = key
                    it[RecordPropertyTable.value] = value
                }
            }
            .map {
                Property(
                    it[RecordPropertyTable.id].value,
                    it[RecordPropertyTable.key],
                    it[RecordPropertyTable.value]
                )
            }

    private fun toPredicate(userId: UUID, filter: AccountTransactionFilterTO): SqlExpressionBuilder.() -> Op<Boolean> {
        return {
            listOfNotNull(
                AccountTransactionTable.userId eq userId,
                filter.fromDate?.let {
                    AccountTransactionTable.dateTime.date() greaterEq it.toJavaLocalDate()
                },
                filter.toDate?.let {
                    AccountTransactionTable.dateTime.date() lessEq it.toJavaLocalDate()
                }
            )
                .reduce { acc, op -> acc and op }
        }
    }

    private fun Query.toTransactions(): List<AccountTransaction> = this
        .groupBy { it[AccountTransactionTable.id].value }
        .map { (_, rows) -> rows.toTransaction() }

    private fun List<ResultRow>.toTransaction(
    ): AccountTransaction {
        return AccountTransaction(
            id = this.first()[AccountTransactionTable.id].value,
            userId = this.first()[AccountTransactionTable.userId],
            externalId = this.first()[AccountTransactionTable.externalId],
            dateTime = this.first()[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = toRecords(),
            properties = toTransactionProperties()
        )
    }

    private fun List<ResultRow>.toTransactionProperties(): List<Property> = this
        .groupBy { it.getOrNull(TransactionPropertyTable.id)?.value }
        .filter { it.key != null }
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
            labels = this.first()[AccountRecordTable.labels].asLabels(),
            properties = this.toRecordProperties(),
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
