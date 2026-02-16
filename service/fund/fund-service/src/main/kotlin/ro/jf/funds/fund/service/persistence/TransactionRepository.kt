package ro.jf.funds.fund.service.persistence

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.domain.TransactionRecord
import ro.jf.funds.fund.service.persistence.RecordRepository.RecordTable
import ro.jf.funds.platform.api.model.*
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.persistence.applyFilterIfPresent
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.*
import java.util.UUID.randomUUID

class TransactionRepository(
    private val database: Database,
) {
    object TransactionTable : UUIDTable("transaction") {
        val userId = uuid("user_id")
        val externalId = varchar("external_id", 100)
        val type = varchar("type", 50)
        val dateTime = datetime("date_time")
    }

    suspend fun findById(userId: UUID, transactionId: UUID): Transaction? = blockingTransaction {
        (TransactionTable leftJoin RecordTable)
            .selectAll()
            .where { TransactionTable.userId eq userId and (TransactionTable.id eq transactionId) }
            .toTransactions()
            .singleOrNull()
    }

    suspend fun list(
        userId: UUID,
        filter: TransactionFilterTO,
    ): List<Transaction> = blockingTransaction {
        val matchingTransactionIds = TransactionTable
            .leftJoin(RecordTable)
            .select(TransactionTable.id)
            .where(toPredicate(userId, filter))
            .applyFilterIfPresent(filter.fundId) { RecordTable.fundId eq it }
            .applyFilterIfPresent(filter.accountId) { RecordTable.accountId eq it }
            .map { it[TransactionTable.id].value }
            .distinct()

        if (matchingTransactionIds.isEmpty()) return@blockingTransaction emptyList()

        TransactionTable
            .leftJoin(RecordTable)
            .selectAll()
            .where { TransactionTable.userId eq userId and (TransactionTable.id inList matchingTransactionIds) }
            .toTransactions()
    }

    suspend fun save(userId: UUID, command: CreateTransactionTO): Transaction = blockingTransaction {
        saveTransaction(userId, command)
    }

    suspend fun saveAll(
        userId: UUID, requests: CreateTransactionsTO,
    ): List<Transaction> = blockingTransaction {
        val storedTransactions =
            TransactionTable.batchInsert(
                requests.transactions,
                ignore = true,
                shouldReturnGeneratedValues = false
            ) { command: CreateTransactionTO ->
                this[TransactionTable.id] = randomUUID()
                this[TransactionTable.userId] = userId
                this[TransactionTable.externalId] = command.externalId
                this[TransactionTable.type] = command.type.name
                this[TransactionTable.dateTime] = command.dateTime.toJavaLocalDateTime()
            }

        val transactionRequestsByExternalId = requests.transactions.associateBy { it.externalId }
        val transactionIdsToTransactionRequests = storedTransactions
            .associate { it[TransactionTable.id].value to transactionRequestsByExternalId[it[TransactionTable.externalId]]!! }

        val recordRequestsByTransactionId = transactionIdsToTransactionRequests
            .mapValues { (_, request) -> request.records }
        val storedRecordsByTransactionId = saveRecords(userId, recordRequestsByTransactionId)

        storedTransactions
            .map {
                val transactionId = it[TransactionTable.id].value
                val transactionType = TransactionType.valueOf(it[TransactionTable.type])
                val records = storedRecordsByTransactionId[transactionId] ?: emptyList()
                toTransaction(
                    id = transactionId,
                    userId = it[TransactionTable.userId],
                    externalId = it[TransactionTable.externalId],
                    type = transactionType,
                    dateTime = it[TransactionTable.dateTime].toKotlinLocalDateTime(),
                    records = records
                )
            }
    }

    suspend fun deleteByUserId(userId: UUID): Unit = blockingTransaction {
        RecordTable.deleteWhere { RecordTable.userId eq userId }
        TransactionTable.deleteWhere { TransactionTable.userId eq userId }
    }

    suspend fun deleteById(userId: UUID, transactionId: UUID): Unit = blockingTransaction {
        RecordTable.deleteWhere { RecordTable.userId eq userId and (RecordTable.transactionId eq transactionId) }
        TransactionTable.deleteWhere { TransactionTable.userId eq userId and (TransactionTable.id eq transactionId) }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        RecordTable.deleteAll()
        TransactionTable.deleteAll()
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
        TransactionTable.insert {
            it[TransactionTable.userId] = userId
            it[TransactionTable.externalId] = command.externalId
            it[TransactionTable.type] = command.type.name
            it[dateTime] = command.dateTime.toJavaLocalDateTime()
        }.let {
            TransactionMetadata(
                id = it[TransactionTable.id].value,
                userId = it[TransactionTable.userId],
                externalId = it[TransactionTable.externalId],
                dateTime = it[TransactionTable.dateTime].toKotlinLocalDateTime()
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
            RecordTable.batchInsert(transactionIdsToRecordRequest, shouldReturnGeneratedValues = false) {
                this[RecordTable.id] = randomUUID()
                this[RecordTable.userId] = userId
                this[RecordTable.transactionId] = it.first
                this[RecordTable.accountId] = it.second.accountId
                this[RecordTable.fundId] = extractFundId(it.second)
                this[RecordTable.amount] = it.second.amount
                this[RecordTable.unitType] = it.second.unit.type.value
                this[RecordTable.unit] = it.second.unit.value
                this[RecordTable.labels] = it.second.labels.asString()
                this[RecordTable.note] = it.second.note
            }

        return transactionIdsToRecordRequest
            .zip(storedRecords)
            .map { (transactionIdAndRequest, storedRecord) ->
                val (transactionId, request) = transactionIdAndRequest
                val record = toTransactionRecord(
                    id = storedRecord[RecordTable.id].value,
                    accountId = storedRecord[RecordTable.accountId],
                    fundId = storedRecord[RecordTable.fundId],
                    amount = storedRecord[RecordTable.amount],
                    unitType = request.unit.type,
                    unitValue = storedRecord[RecordTable.unit],
                    labels = storedRecord[RecordTable.labels].asLabels(),
                    note = storedRecord[RecordTable.note],
                )
                transactionId to record
            }
            .groupBy({ it.first }) { it.second }
    }

    private fun insertRecord(
        userId: UUID,
        transactionId: UUID,
        record: CreateTransactionRecordTO,
    ): TransactionRecord {
        val insertResult = RecordTable.insert {
            it[RecordTable.userId] = userId
            it[RecordTable.transactionId] = transactionId
            it[accountId] = record.accountId
            it[fundId] = extractFundId(record)
            it[amount] = record.amount
            it[unit] = record.unit.value
            it[unitType] = record.unit.type.value
            it[labels] = record.labels.asString()
            it[note] = record.note
        }
        return toTransactionRecord(
            id = insertResult[RecordTable.id].value,
            accountId = insertResult[RecordTable.accountId],
            fundId = insertResult[RecordTable.fundId],
            amount = insertResult[RecordTable.amount],
            unitType = record.unit.type,
            unitValue = insertResult[RecordTable.unit],
            labels = insertResult[RecordTable.labels].asLabels(),
            note = insertResult[RecordTable.note],
        )
    }

    private fun extractFundId(record: CreateTransactionRecordTO): UUID {
        return record.fundId
    }

    private fun toTransactionRecord(
        id: UUID,
        accountId: UUID,
        fundId: UUID,
        amount: com.ionspin.kotlin.bignum.decimal.BigDecimal,
        unitType: UnitType,
        unitValue: String,
        labels: List<Label>,
        note: String?,
    ): TransactionRecord = when (unitType) {
        UnitType.CURRENCY -> TransactionRecord.CurrencyRecord(
            id = id,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Currency(unitValue),
            labels = labels,
            note = note,
        )
        UnitType.INSTRUMENT -> TransactionRecord.InstrumentRecord(
            id = id,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Instrument(unitValue),
            labels = labels,
            note = note,
        )
    }

    private fun toPredicate(userId: UUID, filter: TransactionFilterTO): SqlExpressionBuilder.() -> Op<Boolean> {
        return {
            listOfNotNull(
                TransactionTable.userId eq userId,
                filter.fromDate?.let {
                    TransactionTable.dateTime.date() greaterEq it.toJavaLocalDate()
                },
                filter.toDate?.let {
                    TransactionTable.dateTime.date() lessEq it.toJavaLocalDate()
                }
            )
                .reduce { acc, op -> acc and op }
        }
    }

    private fun Query.toTransactions(): List<Transaction> = this
        .groupBy { it[TransactionTable.id].value }
        .map { (_, rows) -> rows.toTransaction() }

    private fun List<ResultRow>.toTransaction(): Transaction {
        return toTransaction(
            id = this.first()[TransactionTable.id].value,
            userId = this.first()[TransactionTable.userId],
            externalId = this.first()[TransactionTable.externalId],
            type = TransactionType.valueOf(this.first()[TransactionTable.type]),
            dateTime = this.first()[TransactionTable.dateTime].toKotlinLocalDateTime(),
            records = toRecords()
        )
    }

    private fun List<ResultRow>.toRecords(): List<TransactionRecord> = this
        .groupBy { it[RecordTable.id].value }
        .map { (_, rows) -> rows.toRecord() }

    private fun List<ResultRow>.toRecord(): TransactionRecord {
        val row = this.first()
        return toTransactionRecord(
            id = row[RecordTable.id].value,
            accountId = row[RecordTable.accountId],
            fundId = row[RecordTable.fundId],
            amount = row[RecordTable.amount],
            unitType = UnitType.entries.first { it.value == row[RecordTable.unitType] },
            unitValue = row[RecordTable.unit],
            labels = row[RecordTable.labels].asLabels(),
            note = row[RecordTable.note],
        )
    }

    private fun toTransaction(
        id: UUID,
        userId: UUID,
        externalId: String,
        type: TransactionType,
        dateTime: kotlinx.datetime.LocalDateTime,
        records: List<TransactionRecord>,
    ): Transaction {
        val currencyRecords = records.filterIsInstance<TransactionRecord.CurrencyRecord>()
        return when (type) {
            TransactionType.SINGLE_RECORD -> Transaction.SingleRecord(
                id = id,
                userId = userId,
                externalId = externalId,
                dateTime = dateTime,
                record = currencyRecords.firstOrNull()
                    ?: throw IllegalStateException("SINGLE_RECORD transaction must have exactly 1 record")
            )

            TransactionType.TRANSFER -> Transaction.Transfer(
                id = id,
                userId = userId,
                externalId = externalId,
                dateTime = dateTime,
                sourceRecord = currencyRecords.getOrNull(0)
                    ?: throw IllegalStateException("TRANSFER transaction must have source record"),
                destinationRecord = currencyRecords.getOrNull(1)
                    ?: throw IllegalStateException("TRANSFER transaction must have destination record")
            )

            TransactionType.EXCHANGE -> Transaction.Exchange(
                id = id,
                userId = userId,
                externalId = externalId,
                dateTime = dateTime,
                sourceRecord = currencyRecords.getOrNull(0)
                    ?: throw IllegalStateException("EXCHANGE transaction must have source record"),
                destinationRecord = currencyRecords.getOrNull(1)
                    ?: throw IllegalStateException("EXCHANGE transaction must have destination record"),
                feeRecord = currencyRecords.getOrNull(2)
            )

            TransactionType.OPEN_POSITION -> Transaction.OpenPosition(
                id = id,
                userId = userId,
                externalId = externalId,
                dateTime = dateTime,
                currencyRecord = currencyRecords.firstOrNull()
                    ?: throw IllegalStateException("OPEN_POSITION transaction must have currency record"),
                instrumentRecord = records.filterIsInstance<TransactionRecord.InstrumentRecord>().firstOrNull()
                    ?: throw IllegalStateException("OPEN_POSITION transaction must have instrument record")
            )

            TransactionType.CLOSE_POSITION -> Transaction.ClosePosition(
                id = id,
                userId = userId,
                externalId = externalId,
                dateTime = dateTime,
                currencyRecord = currencyRecords.firstOrNull()
                    ?: throw IllegalStateException("CLOSE_POSITION transaction must have currency record"),
                instrumentRecord = records.filterIsInstance<TransactionRecord.InstrumentRecord>().firstOrNull()
                    ?: throw IllegalStateException("CLOSE_POSITION transaction must have instrument record")
            )
        }
    }
}
