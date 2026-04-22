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
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.*
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
        val category = varchar("category", 50).nullable()
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
            this[AnalyticsRecordTable.category] = record.category?.value
        }
        records
    }

    suspend fun getBucketedUnitAmounts(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): BucketedUnitAmounts = blockingTransaction {
        val bucketFunction = dateTrunc(interval.granularity, AnalyticsRecordTable.dateTime)
        val totalAmount = AnalyticsRecordTable.amount.sum()

        val truncatedFrom = interval.truncate(interval.from)
        val rows = AnalyticsRecordTable
            .select(bucketFunction, AnalyticsRecordTable.unit, totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime greaterEq interval.from.toJavaLocalDateTime() }
            .andWhere { AnalyticsRecordTable.dateTime lessEq interval.to.toJavaLocalDateTime() }
            .applyFilter(filter)
            .groupBy(bucketFunction, AnalyticsRecordTable.unit)
            .orderBy(bucketFunction)
            .groupBy { row ->
                val dateTime = row[bucketFunction].toKotlinLocalDateTime()
                if (dateTime == truncatedFrom) interval.from else dateTime
            }
            .mapValues { (_, rows) ->
                UnitAmounts(rows.associate { row ->
                    row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
                })
            }
        BucketedUnitAmounts(rows)
    }

    suspend fun getBucketedGroupedUnitAmounts(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        groupBy: GroupingCriteria,
    ): BucketedGroupedUnitAmounts = blockingTransaction {
        val bucket = dateTrunc(interval.granularity, AnalyticsRecordTable.dateTime)
        val totalAmount = AnalyticsRecordTable.amount.sum()
        val groupColumn = groupBy.toColumn()

        val truncatedFrom = interval.truncate(interval.from)
        val rows = AnalyticsRecordTable
            .select(bucket, groupColumn, AnalyticsRecordTable.unit, totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime greaterEq interval.from.toJavaLocalDateTime() }
            .andWhere { AnalyticsRecordTable.dateTime lessEq interval.to.toJavaLocalDateTime() }
            .applyFilter(filter)
            .groupBy(bucket, groupColumn, AnalyticsRecordTable.unit)
            .orderBy(bucket)
            .toList()

        val result = rows
            .groupBy { row ->
                val dateTime = row[bucket].toKotlinLocalDateTime()
                if (dateTime == truncatedFrom) interval.from else dateTime
            }
            .mapValues { (_, bucketRows) ->
                bucketRows
                    .groupBy { row -> row.extractGroupKey(groupBy) }
                    .mapValues { (_, groupRows) ->
                        UnitAmounts(groupRows.associate { row ->
                            row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
                        })
                    }
            }
        BucketedGroupedUnitAmounts(result)
    }

    suspend fun getUnitAmountsBefore(
        userId: Uuid,
        before: LocalDateTime,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): UnitAmounts = blockingTransaction {
        val totalAmount = AnalyticsRecordTable.amount.sum()

        val result = AnalyticsRecordTable
            .select(AnalyticsRecordTable.unit, totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
            .applyFilter(filter)
            .groupBy(AnalyticsRecordTable.unit)
            .associate { row ->
                row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
            }
        UnitAmounts(result)
    }

    suspend fun getGroupedUnitAmountsBefore(
        userId: Uuid,
        before: LocalDateTime,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        groupBy: GroupingCriteria,
    ): GroupedUnitAmounts = blockingTransaction {
        val totalAmount = AnalyticsRecordTable.amount.sum()
        val groupColumn = groupBy.toColumn()

        val rows = AnalyticsRecordTable
            .select(groupColumn, AnalyticsRecordTable.unit, totalAmount)
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
            .applyFilter(filter)
            .groupBy(groupColumn, AnalyticsRecordTable.unit)
            .toList()

        GroupedUnitAmounts(rows
            .groupBy { row -> row.extractGroupKey(groupBy) }
            .mapValues { (_, groupRows) ->
                UnitAmounts(groupRows.associate { row ->
                    row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
                })
            })
    }

    private fun GroupingCriteria.toColumn(): Column<*> = when (this) {
        GroupingCriteria.CURRENCY -> AnalyticsRecordTable.unit
        GroupingCriteria.ACCOUNT -> AnalyticsRecordTable.accountId
        GroupingCriteria.FUND -> AnalyticsRecordTable.fundId
        GroupingCriteria.CATEGORY -> AnalyticsRecordTable.category
    }

    private fun ResultRow.extractGroupKey(groupBy: GroupingCriteria): String? = when (groupBy) {
        GroupingCriteria.CURRENCY -> this[AnalyticsRecordTable.unit].value
        GroupingCriteria.ACCOUNT -> this[AnalyticsRecordTable.accountId].toString()
        GroupingCriteria.FUND -> this[AnalyticsRecordTable.fundId].toString()
        GroupingCriteria.CATEGORY -> this[AnalyticsRecordTable.category]
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
                    filter.units.map<FinancialUnit, Op<Boolean>> {
                        AnalyticsRecordTable.unit.contains(Json.encodeToString(FinancialUnit.serializer(), it))
                    }.reduce { acc, op -> acc or op }
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
