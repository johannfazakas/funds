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
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Category
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.jvm.persistence.bigDecimal
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.*

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
        filter: AnalyticsDbRecordFilter = AnalyticsDbRecordFilter(),
        groupBy: GroupingCriteria? = null,
    ): BucketedGroupedUnitAmounts = blockingTransaction {
        val bucket = dateTrunc(interval.granularity, AnalyticsRecordTable.dateTime)
        val groupColumn = groupBy?.toColumn()
        val totalAmount = AnalyticsRecordTable.amount.sum()

        BucketedGroupedUnitAmounts(
            AnalyticsRecordTable
                .select(listOfNotNull(bucket, groupColumn, AnalyticsRecordTable.unit, totalAmount).distinct())
                .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
                .andWhere { AnalyticsRecordTable.dateTime greaterEq interval.from.toJavaLocalDateTime() }
                .andWhere { AnalyticsRecordTable.dateTime less interval.to.toJavaLocalDateTime() }
                .applyFilter(filter)
                .groupBy(*listOfNotNull(bucket, groupColumn, AnalyticsRecordTable.unit).distinct().toTypedArray())
                .orderBy(bucket)
                .toList()
                .groupBy { row ->
                    val dateTime = row[bucket].toKotlinLocalDateTime()
                    if (dateTime == interval.truncate(interval.from)) interval.from else dateTime
                }
                .mapValues { (_, bucketRows) ->
                    GroupedUnitAmounts(
                        bucketRows
                            .groupBy { row -> groupBy?.let { row.extractGroupKey(it) } ?: GroupKey.Ungrouped }
                            .mapValues { (_, groupRows) ->
                                UnitAmounts(groupRows.associate { row ->
                                    row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
                                })
                            })
                }
        )
    }

    suspend fun getUnitAmountsBefore(
        userId: Uuid,
        before: LocalDateTime,
        filter: AnalyticsDbRecordFilter = AnalyticsDbRecordFilter(),
        groupBy: GroupingCriteria? = null,
    ): GroupedUnitAmounts = blockingTransaction {
        val groupColumn = groupBy?.toColumn()
        val totalAmount = AnalyticsRecordTable.amount.sum()

        GroupedUnitAmounts(
            AnalyticsRecordTable
                .select(listOfNotNull(groupColumn, AnalyticsRecordTable.unit, totalAmount).distinct())
                .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
                .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
                .applyFilter(filter)
                .groupBy(*listOfNotNull(groupColumn, AnalyticsRecordTable.unit).distinct().toTypedArray())
                .toList()
                .groupBy { row -> groupBy?.let { row.extractGroupKey(it) } ?: GroupKey.Ungrouped }
                .mapValues { (_, groupRows) ->
                    UnitAmounts(groupRows.associate { row ->
                        row[AnalyticsRecordTable.unit] to (row[totalAmount] ?: BigDecimal.ZERO)
                    })
                })
    }

    suspend fun getRecords(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsDbRecordFilter = AnalyticsDbRecordFilter(),
    ): List<AnalyticsRecord> = blockingTransaction {
        AnalyticsRecordTable
            .selectAll()
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime greaterEq interval.from.toJavaLocalDateTime() }
            .andWhere { AnalyticsRecordTable.dateTime less interval.to.toJavaLocalDateTime() }
            .applyFilter(filter)
            .orderBy(AnalyticsRecordTable.dateTime)
            .map { it.toAnalyticsRecord() }
    }

    suspend fun getRecordsBefore(
        userId: Uuid,
        before: LocalDateTime,
        filter: AnalyticsDbRecordFilter = AnalyticsDbRecordFilter(),
    ): List<AnalyticsRecord> = blockingTransaction {
        AnalyticsRecordTable
            .selectAll()
            .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
            .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
            .applyFilter(filter)
            .orderBy(AnalyticsRecordTable.dateTime)
            .map { it.toAnalyticsRecord() }
    }

    private fun ResultRow.toAnalyticsRecord() = AnalyticsRecord(
        id = Uuid.fromString(this[AnalyticsRecordTable.id].toString()),
        userId = Uuid.fromString(this[AnalyticsRecordTable.userId].toString()),
        fundId = Uuid.fromString(this[AnalyticsRecordTable.fundId].toString()),
        accountId = Uuid.fromString(this[AnalyticsRecordTable.accountId].toString()),
        transactionId = Uuid.fromString(this[AnalyticsRecordTable.transactionId].toString()),
        transactionType = TransactionType.valueOf(this[AnalyticsRecordTable.transactionType]),
        dateTime = this[AnalyticsRecordTable.dateTime].toKotlinLocalDateTime(),
        amount = this[AnalyticsRecordTable.amount],
        unit = this[AnalyticsRecordTable.unit],
        category = this[AnalyticsRecordTable.category]?.let { Category(it) },
    )

    private fun GroupingCriteria.toColumn(): Column<*> = when (this) {
        GroupingCriteria.FINANCIAL_UNIT -> AnalyticsRecordTable.unit
        GroupingCriteria.ACCOUNT -> AnalyticsRecordTable.accountId
        GroupingCriteria.FUND -> AnalyticsRecordTable.fundId
        GroupingCriteria.CATEGORY -> AnalyticsRecordTable.category
    }

    private fun ResultRow.extractGroupKey(groupBy: GroupingCriteria): GroupKey = when (groupBy) {
        GroupingCriteria.FINANCIAL_UNIT -> GroupKey.ByFinancialUnit(this[AnalyticsRecordTable.unit].value)
        GroupingCriteria.ACCOUNT -> GroupKey.ByAccount(this[AnalyticsRecordTable.accountId].toString())
        GroupingCriteria.FUND -> GroupKey.ByFund(this[AnalyticsRecordTable.fundId].toString())
        GroupingCriteria.CATEGORY -> GroupKey.ByCategory(this[AnalyticsRecordTable.category])
    }

    private fun Query.applyFilter(filter: AnalyticsDbRecordFilter): Query = this
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
        .let { query ->
            if (filter.transactionTypes.isNotEmpty())
                query.andWhere { AnalyticsRecordTable.transactionType inList filter.transactionTypes.map { it.name } }
            else query
        }
        .let { query ->
            if (filter.unitTypes.isNotEmpty())
                query.andWhere {
                    filter.unitTypes.map<_, Op<Boolean>> { unitType ->
                        AnalyticsRecordTable.unit.contains("""{"type":"${unitType.value}"}""")
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
