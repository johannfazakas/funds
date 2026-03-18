package ro.jf.funds.analytics.service.persistence

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.json
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
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
        val labels = json<List<String>>("labels", Json.Default)
    }

    suspend fun saveAll(records: List<AnalyticsRecord>): List<AnalyticsRecord> = blockingTransaction {
        AnalyticsRecordTable.batchInsert(records) { record ->
            this[AnalyticsRecordTable.id] = record.id
            this[AnalyticsRecordTable.userId] = record.userId
            this[AnalyticsRecordTable.transactionId] = record.transactionId
            this[AnalyticsRecordTable.dateTime] = record.dateTime.toJavaLocalDateTime()
            this[AnalyticsRecordTable.accountId] = record.accountId
            this[AnalyticsRecordTable.fundId] = record.fundId
            this[AnalyticsRecordTable.amount] = record.amount
            this[AnalyticsRecordTable.unit] = record.unit
            this[AnalyticsRecordTable.transactionType] = record.transactionType.name
            this[AnalyticsRecordTable.labels] = record.labels.map { it.value }
        }
        records
    }
}
