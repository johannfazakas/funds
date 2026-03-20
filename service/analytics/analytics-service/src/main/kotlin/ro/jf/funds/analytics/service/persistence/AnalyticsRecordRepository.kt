package ro.jf.funds.analytics.service.persistence

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.json
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.domain.AnalyticsValueAggregate
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.jvm.persistence.bigDecimal
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.UUID

class AnalyticsRecordRepository(
    private val database: Database,
) {
    object AnalyticsRecordTable : UUIDTable("analytics_record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id")
        val dateTime = datetime("date_time")
        val accountId = uuid("account_id")
        val fundId = uuid("fund_id")
        val amount = bigDecimal("amount", 20, 8)
        val unit = json<FinancialUnit>("unit", Json.Default)
        val transactionType = varchar("transaction_type", 50)
        val labels = json<List<String>>("labels", Json.Default)
    }

    suspend fun saveAll(records: List<AnalyticsRecord>): List<AnalyticsRecord> = blockingTransaction {
        AnalyticsRecordTable.batchInsert(records) { record ->
            this[AnalyticsRecordTable.id] = record.id.toJavaUuid()
            this[AnalyticsRecordTable.userId] = record.userId.toJavaUuid()
            this[AnalyticsRecordTable.transactionId] = record.transactionId.toJavaUuid()
            this[AnalyticsRecordTable.dateTime] = record.dateTime.toJavaLocalDateTime()
            this[AnalyticsRecordTable.accountId] = record.accountId.toJavaUuid()
            this[AnalyticsRecordTable.fundId] = record.fundId.toJavaUuid()
            this[AnalyticsRecordTable.amount] = record.amount
            this[AnalyticsRecordTable.unit] = record.unit
            this[AnalyticsRecordTable.transactionType] = record.transactionType.name
            this[AnalyticsRecordTable.labels] = record.labels.map { it.value }
        }
        records
    }

    suspend fun getValueAggregates(
        userId: Uuid,
        granularity: TimeGranularity,
        from: LocalDateTime,
        to: LocalDateTime,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): List<AnalyticsValueAggregate> = blockingTransaction {
        val bucket = dateTrunc(granularity, AnalyticsRecordTable.dateTime)
        val totalAmount = AnalyticsRecordTable.amount.sum()

        AnalyticsRecordTable
            .select(bucket, totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime greaterEq from.toJavaLocalDateTime() }
            .andWhere { AnalyticsRecordTable.dateTime less to.toJavaLocalDateTime() }
            .applyFilter(filter)
            .groupBy(bucket)
            .orderBy(bucket)
            .map { row ->
                AnalyticsValueAggregate(
                    dateTime = (row[bucket]).toKotlinLocalDateTime(),
                    sum = row[totalAmount] ?: BigDecimal.ZERO,
                )
            }
    }

    suspend fun getSumBefore(
        userId: Uuid,
        before: LocalDateTime,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): BigDecimal = blockingTransaction {
        val totalAmount = AnalyticsRecordTable.amount.sum()

        AnalyticsRecordTable
            .select(totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
            .applyFilter(filter)
            .single()[totalAmount] ?: BigDecimal.ZERO
    }

    private fun Query.applyFilter(filter: AnalyticsRecordFilter): Query = this
        .let { query ->
            if (filter.fundIds.isNotEmpty())
                query.andWhere { AnalyticsRecordTable.fundId inList filter.fundIds.map { it.toJavaUuid() } }
            else query
        }
        .let { query ->
            if (filter.units.isNotEmpty())
                query.andWhere {
                    filter.units.map<FinancialUnit, Op<Boolean>> { AnalyticsRecordTable.unit.contains(it) }
                        .reduce { acc, op -> acc or op }
                }
            else query
        }

    private fun dateTrunc(
        granularity: TimeGranularity,
        column: Column<java.time.LocalDateTime>,
    ): CustomFunction<java.time.LocalDateTime> =
        CustomFunction("date_trunc", column.columnType, stringLiteral(granularity.toSqlValue()), column)

    private fun TimeGranularity.toSqlValue(): String = when (this) {
        TimeGranularity.DAILY -> "day"
        TimeGranularity.WEEKLY -> "week"
        TimeGranularity.MONTHLY -> "month"
        TimeGranularity.YEARLY -> "year"
    }

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(this.toString())
}
