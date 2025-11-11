package ro.jf.funds.historicalpricing.service.persistence

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.selectAll
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.toFinancialUnit
import ro.jf.funds.historicalpricing.service.domain.Conversion
import java.util.*

class ConversionRepository(
    private val database: Database,
) {

    object Table : UUIDTable("conversion") {
        val sourceUnit = varchar("source_unit", 50)
        val sourceType = varchar("source_type", 50)
        val targetCurrency = varchar("target_currency", 50)
        val date = date("date")
        val price = decimal("price", 20, 8)
    }

    class DAO(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<DAO>(Table)

        var sourceUnit by Table.sourceUnit
        var sourceType by Table.sourceType
        var targetCurrency by Table.targetCurrency
        var date by Table.date
        var price by Table.price
    }

    suspend fun getHistoricalPrice(
        source: FinancialUnit,
        target: Currency,
        date: LocalDate,
    ): Conversion? = blockingTransaction {
        Table
            .selectAll()
            .where {
                (Table.sourceUnit eq source.value) and
                        (Table.sourceType eq source.type.value) and
                        (Table.targetCurrency eq target.value) and
                        (Table.date eq date.toJavaLocalDate())
            }
            .mapNotNull { it.toModel() }
            .singleOrNull()
    }

    suspend fun getHistoricalPrices(
        source: FinancialUnit,
        target: Currency,
        dates: List<LocalDate>,
    ): List<Conversion> = blockingTransaction {
        Table
            .selectAll()
            .where {
                (Table.sourceUnit eq source.value) and
                        (Table.sourceType eq source.type.value) and
                        (Table.targetCurrency eq target.value) and
                        (Table.date inList dates.map { it.toJavaLocalDate() })
            }
            .map { it.toModel() }
    }

    suspend fun saveHistoricalPrice(
        historicalPrice: Conversion,
    ): Unit = blockingTransaction {
        DAO.new {
            sourceUnit = historicalPrice.source.value
            sourceType = historicalPrice.source.type.value
            targetCurrency = historicalPrice.target.value
            date = historicalPrice.date.toJavaLocalDate()
            price = historicalPrice.price
        }
    }

    private fun ResultRow.toModel() =
        Conversion(
            source = toFinancialUnit(this[Table.sourceType], this[Table.sourceUnit]),
            target = Currency(this[Table.targetCurrency]),
            date = this[Table.date].let {
                LocalDate(it.year, it.monthValue, it.dayOfMonth)
            },
            price = this[Table.price]
        )
}
