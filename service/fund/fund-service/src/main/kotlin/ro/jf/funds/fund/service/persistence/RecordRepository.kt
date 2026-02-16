package ro.jf.funds.fund.service.persistence

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.service.domain.Record
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.TransactionRepository.TransactionTable
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.api.model.UnitType
import ro.jf.funds.platform.api.model.asLabels
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.persistence.applyFilterIfPresent
import ro.jf.funds.platform.jvm.persistence.bigDecimal
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.platform.jvm.persistence.toExposedSortOrder
import java.util.*

class RecordRepository(
    private val database: Database,
) {
    object RecordTable : UUIDTable("record") {
        val userId = uuid("user_id")
        val transactionId = uuid("transaction_id").references(TransactionRepository.TransactionTable.id)
        val accountId = uuid("account_id").references(AccountRepository.AccountTable.id)
        val fundId = uuid("fund_id").references(FundRepository.FundTable.id)
        val amount = bigDecimal("amount", 20, 8)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
        val labels = varchar("labels", 100)
        val note = varchar("note", 500).nullable()
    }

    suspend fun list(
        userId: UUID,
        filter: RecordFilter? = null,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<RecordSortField>? = null,
    ): PagedResult<Record> = blockingTransaction {
        val baseQuery = (RecordTable innerJoin TransactionTable)
            .selectAll()
            .where { RecordTable.userId eq userId }
            .applyFiltering(filter)

        val total = baseQuery.count()

        val records = (RecordTable innerJoin TransactionTable)
            .selectAll()
            .where { RecordTable.userId eq userId }
            .applyFiltering(filter)
            .applySorting(sortRequest)
            .applyPagination(pageRequest)
            .map { row ->
                toRecord(
                    id = row[RecordTable.id].value,
                    transactionId = row[RecordTable.transactionId],
                    dateTime = row[TransactionTable.dateTime].toKotlinLocalDateTime(),
                    accountId = row[RecordTable.accountId],
                    fundId = row[RecordTable.fundId],
                    amount = row[RecordTable.amount],
                    unitType = UnitType.entries.first { it.value == row[RecordTable.unitType] },
                    unitValue = row[RecordTable.unit],
                    labels = row[RecordTable.labels].asLabels(),
                    note = row[RecordTable.note],
                )
            }

        PagedResult(records, total)
    }

    private fun Query.applyFiltering(filter: RecordFilter?): Query {
        if (filter == null) return this
        return this
            .applyFilterIfPresent(filter.accountId) { RecordTable.accountId eq it }
            .applyFilterIfPresent(filter.fundId) { RecordTable.fundId eq it }
            .applyFilterIfPresent(filter.unit) { RecordTable.unit eq it }
            .applyFilterIfPresent(filter.label) { RecordTable.labels like "%$it%" }
            .applyFilterIfPresent(filter.fromDate) { TransactionTable.dateTime.date() greaterEq it.toJavaLocalDate() }
            .applyFilterIfPresent(filter.toDate) { TransactionTable.dateTime.date() lessEq it.toJavaLocalDate() }
    }

    private fun Query.applySorting(sortRequest: SortRequest<RecordSortField>?): Query =
        sortRequest?.let {
            val sortColumn = when (it.field) {
                RecordSortField.DATE -> TransactionTable.dateTime
                RecordSortField.AMOUNT -> RecordTable.amount
            }
            orderBy(sortColumn to it.order.toExposedSortOrder())
        } ?: this

    private fun Query.applyPagination(pageRequest: PageRequest?): Query =
        pageRequest?.let { limit(it.limit).offset(it.offset.toLong()) } ?: this

    private fun toRecord(
        id: UUID,
        transactionId: UUID,
        dateTime: kotlinx.datetime.LocalDateTime,
        accountId: UUID,
        fundId: UUID,
        amount: com.ionspin.kotlin.bignum.decimal.BigDecimal,
        unitType: UnitType,
        unitValue: String,
        labels: List<Label>,
        note: String?,
    ): Record = when (unitType) {
        UnitType.CURRENCY -> Record.CurrencyRecord(
            id = id,
            transactionId = transactionId,
            dateTime = dateTime,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Currency(unitValue),
            labels = labels,
            note = note,
        )
        UnitType.INSTRUMENT -> Record.InstrumentRecord(
            id = id,
            transactionId = transactionId,
            dateTime = dateTime,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Instrument(unitValue),
            labels = labels,
            note = note,
        )
    }
}
