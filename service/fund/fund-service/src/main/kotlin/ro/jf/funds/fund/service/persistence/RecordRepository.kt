package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.domain.TransactionRecord
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.model.UnitType
import ro.jf.funds.platform.api.model.asLabels
import ro.jf.funds.platform.jvm.persistence.bigDecimal
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
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
    }

    suspend fun list(userId: UUID, filter: RecordFilter? = null): List<TransactionRecord> = blockingTransaction {
        RecordTable.selectAll()
            .where { RecordTable.userId eq userId }
            .let { query ->
                if (filter?.accountId != null) {
                    query.andWhere { RecordTable.accountId eq filter.accountId }
                } else {
                    query
                }
            }
            .let { query ->
                if (filter?.fundId != null) {
                    query.andWhere { RecordTable.fundId eq filter.fundId }
                } else {
                    query
                }
            }
            .map { row ->
                toTransactionRecord(
                    id = row[RecordTable.id].value,
                    accountId = row[RecordTable.accountId],
                    fundId = row[RecordTable.fundId],
                    amount = row[RecordTable.amount],
                    unitType = UnitType.entries.first { it.value == row[RecordTable.unitType] },
                    unitValue = row[RecordTable.unit],
                    labels = row[RecordTable.labels].asLabels()
                )
            }
    }

    private fun toTransactionRecord(
        id: UUID,
        accountId: UUID,
        fundId: UUID,
        amount: com.ionspin.kotlin.bignum.decimal.BigDecimal,
        unitType: UnitType,
        unitValue: String,
        labels: List<Label>,
    ): TransactionRecord = when (unitType) {
        UnitType.CURRENCY -> TransactionRecord.CurrencyRecord(
            id = id,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Currency(unitValue),
            labels = labels
        )
        UnitType.INSTRUMENT -> TransactionRecord.InstrumentRecord(
            id = id,
            accountId = accountId,
            fundId = fundId,
            amount = amount,
            unit = Instrument(unitValue),
            labels = labels
        )
    }
}
