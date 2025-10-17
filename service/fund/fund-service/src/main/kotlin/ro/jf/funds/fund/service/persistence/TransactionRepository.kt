package ro.jf.funds.fund.service.persistence

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.funds.commons.model.asLabels
import ro.jf.funds.commons.model.asString
import ro.jf.funds.commons.model.toFinancialUnit
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.domain.TransactionRecord
import ro.jf.funds.fund.service.persistence.AccountRepository.AccountTable
import ro.jf.funds.fund.service.persistence.FundRepository.FundTable
import java.util.*
import java.util.UUID.randomUUID

class TransactionRepository(
    private val database: Database,
) {
    object AccountTransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val externalId = varchar("external_id", 100)
        val type = varchar("type", 50)
        val dateTime = datetime("date_time")
    }

    object AccountRecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(AccountTransactionTable.id)
        val accountId = uuid("account_id").references(AccountTable.id)
        val fundId = uuid("fund_id").references(FundTable.id)
        val amount = decimal("amount", 20, 8)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
        val labels = varchar("labels", 100)
    }

    suspend fun findById(userId: UUID, transactionId: UUID): Transaction? = blockingTransaction {
        (AccountTransactionTable leftJoin AccountRecordTable)
            .selectAll()
            .where { AccountTransactionTable.userId eq userId and (AccountTransactionTable.id eq transactionId) }
            .toTransactions()
            .singleOrNull()
    }

    suspend fun list(
        userId: UUID,
        filter: TransactionFilterTO,
    ): List<Transaction> = blockingTransaction {
        val query = AccountTransactionTable
            .leftJoin(AccountRecordTable)
            .selectAll()
            .where(toPredicate(userId, filter))
            .let { query ->
                if (filter.fundId != null) {
                    query.andWhere { AccountRecordTable.fundId eq filter.fundId!! }
                } else {
                    query
                }
            }
        query.toTransactions()
    }

    suspend fun save(userId: UUID, command: CreateTransactionTO): Transaction = blockingTransaction {
        saveTransaction(userId, command)
    }

    suspend fun saveAll(
        userId: UUID, requests: CreateTransactionsTO,
    ): List<Transaction> = blockingTransaction {
        val storedTransactions =
            AccountTransactionTable.batchInsert(
                requests.transactions,
                ignore = true,
                shouldReturnGeneratedValues = false
            ) { command: CreateTransactionTO ->
                this[AccountTransactionTable.id] = randomUUID()
                this[AccountTransactionTable.userId] = userId
                this[AccountTransactionTable.externalId] = command.externalId
                this[AccountTransactionTable.type] = command.type.name
                this[AccountTransactionTable.dateTime] = command.dateTime.toJavaLocalDateTime()
            }

        val transactionRequestsByExternalId = requests.transactions.associateBy { it.externalId }
        val transactionIdsToTransactionRequests = storedTransactions
            .associate { it[AccountTransactionTable.id].value to transactionRequestsByExternalId[it[AccountTransactionTable.externalId]]!! }

        val recordRequestsByTransactionId = transactionIdsToTransactionRequests
            .mapValues { (_, request) -> request.records }
        val storedRecordsByTransactionId = saveRecords(userId, recordRequestsByTransactionId)

        storedTransactions
            .map {
                val transactionId = it[AccountTransactionTable.id].value
                val transactionType = TransactionType.valueOf(it[AccountTransactionTable.type])
                val records = storedRecordsByTransactionId[transactionId] ?: emptyList()
                toTransaction(
                    id = transactionId,
                    userId = it[AccountTransactionTable.userId],
                    externalId = it[AccountTransactionTable.externalId],
                    type = transactionType,
                    dateTime = it[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
                    records = records
                )
            }
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

    private fun saveTransaction(
        userId: UUID,
        command: CreateTransactionTO,
    ): Transaction {
        val transactionMetadata = insertTransaction(userId, command)
        val records = command.records.map { recordRequest ->
            insertRecord(userId, transactionMetadata.id, recordRequest)
        }
        return toTransaction(
            id = transactionMetadata.id,
            userId = transactionMetadata.userId,
            externalId = transactionMetadata.externalId,
            type = command.type,
            dateTime = transactionMetadata.dateTime,
            records = records
        )
    }

    private data class TransactionMetadata(
        val id: UUID,
        val userId: UUID,
        val externalId: String,
        val dateTime: kotlinx.datetime.LocalDateTime,
    )

    private fun insertTransaction(
        userId: UUID,
        command: CreateTransactionTO,
    ): TransactionMetadata =
        AccountTransactionTable.insert {
            it[AccountTransactionTable.userId] = userId
            it[AccountTransactionTable.externalId] = command.externalId
            it[AccountTransactionTable.type] = command.type.name
            it[dateTime] = command.dateTime.toJavaLocalDateTime()
        }.let {
            TransactionMetadata(
                id = it[AccountTransactionTable.id].value,
                userId = it[AccountTransactionTable.userId],
                externalId = it[AccountTransactionTable.externalId],
                dateTime = it[AccountTransactionTable.dateTime].toKotlinLocalDateTime()
            )
        }

    private fun saveRecords(
        userId: UUID,
        recordRequestsByTransactionId: Map<UUID, List<CreateTransactionRecordTO>>,
    ): Map<UUID, List<TransactionRecord>> {
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
                this[AccountRecordTable.fundId] = extractFundId(it.second)
                this[AccountRecordTable.amount] = it.second.amount
                this[AccountRecordTable.unitType] = it.second.unit.type.value
                this[AccountRecordTable.unit] = it.second.unit.value
                this[AccountRecordTable.labels] = it.second.labels.asString()
            }

        return transactionIdsToRecordRequest
            .map { it.first }
            .zip(storedRecords)
            .map { (transactionId, storedRecord) ->
                transactionId to TransactionRecord(
                    id = storedRecord[AccountRecordTable.id].value,
                    accountId = storedRecord[AccountRecordTable.accountId],
                    fundId = storedRecord[AccountRecordTable.fundId],
                    amount = storedRecord[AccountRecordTable.amount],
                    unit = toFinancialUnit(
                        storedRecord[AccountRecordTable.unitType],
                        storedRecord[AccountRecordTable.unit]
                    ),
                    labels = storedRecord[AccountRecordTable.labels].asLabels()
                )
            }
            .groupBy({ it.first }) { it.second }
    }

    private fun insertRecord(
        userId: UUID,
        transactionId: UUID,
        record: CreateTransactionRecordTO,
    ) =
        AccountRecordTable.insert {
            it[AccountRecordTable.userId] = userId
            it[AccountRecordTable.transactionId] = transactionId
            it[accountId] = record.accountId
            it[fundId] = extractFundId(record)
            it[amount] = record.amount
            it[unit] = record.unit.value
            it[unitType] = record.unit.type.value
            it[labels] = record.labels.asString()
        }
            .let {
                TransactionRecord(
                    id = it[AccountRecordTable.id].value,
                    accountId = it[AccountRecordTable.accountId],
                    fundId = it[AccountRecordTable.fundId],
                    amount = it[AccountRecordTable.amount],
                    unit = toFinancialUnit(it[AccountRecordTable.unitType], it[AccountRecordTable.unit]),
                    labels = it[AccountRecordTable.labels].asLabels(),
                )
            }

    private fun extractFundId(record: CreateTransactionRecordTO): UUID {
        return record.fundId
    }

    private fun toPredicate(userId: UUID, filter: TransactionFilterTO): SqlExpressionBuilder.() -> Op<Boolean> {
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

    private fun Query.toTransactions(): List<Transaction> = this
        .groupBy { it[AccountTransactionTable.id].value }
        .map { (_, rows) -> rows.toTransaction() }

    private fun List<ResultRow>.toTransaction(): Transaction {
        return toTransaction(
            id = this.first()[AccountTransactionTable.id].value,
            userId = this.first()[AccountTransactionTable.userId],
            externalId = this.first()[AccountTransactionTable.externalId],
            type = TransactionType.valueOf(this.first()[AccountTransactionTable.type]),
            dateTime = this.first()[AccountTransactionTable.dateTime].toKotlinLocalDateTime(),
            records = toRecords()
        )
    }

    private fun List<ResultRow>.toRecords(): List<TransactionRecord> = this
        .groupBy { it[AccountRecordTable.id].value }
        .map { (_, rows) -> rows.toRecord() }

    private fun List<ResultRow>.toRecord(): TransactionRecord =
        TransactionRecord(
            id = this.first()[AccountRecordTable.id].value,
            accountId = this.first()[AccountRecordTable.accountId],
            fundId = this.first()[AccountRecordTable.fundId],
            amount = this.first()[AccountRecordTable.amount],
            unit = toFinancialUnit(this.first()[AccountRecordTable.unitType], this.first()[AccountRecordTable.unit]),
            labels = this.first()[AccountRecordTable.labels].asLabels()
        )

    private fun toTransaction(
        id: UUID,
        userId: UUID,
        externalId: String,
        type: TransactionType,
        dateTime: kotlinx.datetime.LocalDateTime,
        records: List<TransactionRecord>
    ): Transaction = when (type) {
        TransactionType.SINGLE_RECORD -> Transaction.SingleRecord(
            id = id,
            userId = userId,
            externalId = externalId,
            dateTime = dateTime,
            record = records.firstOrNull() ?: throw IllegalStateException("SINGLE_RECORD transaction must have exactly 1 record")
        )
        TransactionType.TRANSFER -> Transaction.Transfer(
            id = id,
            userId = userId,
            externalId = externalId,
            dateTime = dateTime,
            sourceRecord = records.getOrNull(0) ?: throw IllegalStateException("TRANSFER transaction must have source record"),
            destinationRecord = records.getOrNull(1) ?: throw IllegalStateException("TRANSFER transaction must have destination record")
        )
        TransactionType.EXCHANGE -> Transaction.Exchange(
            id = id,
            userId = userId,
            externalId = externalId,
            dateTime = dateTime,
            sourceRecord = records.getOrNull(0) ?: throw IllegalStateException("EXCHANGE transaction must have source record"),
            destinationRecord = records.getOrNull(1) ?: throw IllegalStateException("EXCHANGE transaction must have destination record"),
            feeRecord = records.getOrNull(2)
        )
        TransactionType.OPEN_POSITION -> Transaction.OpenPosition(
            id = id,
            userId = userId,
            externalId = externalId,
            dateTime = dateTime,
            currencyRecord = records.getOrNull(0) ?: throw IllegalStateException("OPEN_POSITION transaction must have currency record"),
            instrumentRecord = records.getOrNull(1) ?: throw IllegalStateException("OPEN_POSITION transaction must have instrument record")
        )
        TransactionType.CLOSE_POSITION -> Transaction.ClosePosition(
            id = id,
            userId = userId,
            externalId = externalId,
            dateTime = dateTime,
            currencyRecord = records.getOrNull(0) ?: throw IllegalStateException("CLOSE_POSITION transaction must have currency record"),
            instrumentRecord = records.getOrNull(1) ?: throw IllegalStateException("CLOSE_POSITION transaction must have instrument record")
        )
    }
}
