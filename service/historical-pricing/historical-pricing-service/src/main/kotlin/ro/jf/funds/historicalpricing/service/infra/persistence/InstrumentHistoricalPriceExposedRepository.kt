package ro.jf.funds.historicalpricing.service.infra.persistence

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
import org.jetbrains.exposed.sql.select
import ro.jf.funds.historicalpricing.service.domain.model.InstrumentHistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentHistoricalPriceRepository
import java.util.*

class InstrumentHistoricalPriceExposedRepository(
    private val database: Database
) : InstrumentHistoricalPriceRepository {
    object Table : UUIDTable("instrument_historical_price") {
        val symbol = varchar("symbol", 50)
        val currency = varchar("currency", 50)
        val date = date("date")
        val price = decimal("price", 20, 8)
    }

    class DAO(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<DAO>(Table)

        var symbol by Table.symbol
        var currency by Table.currency
        var date by Table.date
        var price by Table.price
    }

    override suspend fun getHistoricalPrices(
        symbol: String,
        currency: String,
        date: LocalDate
    ): InstrumentHistoricalPrice? = blockingTransaction {
        Table
            .select {
                (Table.symbol eq symbol) and
                        (Table.currency eq currency) and
                        (Table.date eq date.toJavaLocalDate())
            }
            .mapNotNull { it.toModel() }
            .singleOrNull()
    }

    override suspend fun getHistoricalPrices(
        symbol: String,
        currency: String,
        dates: List<LocalDate>
    ): List<InstrumentHistoricalPrice> = blockingTransaction {
        Table
            .select {
                (Table.symbol eq symbol) and
                        (Table.currency eq currency) and
                        (Table.date inList dates.map { it.toJavaLocalDate() })
            }
            .map { it.toModel() }
    }

    override suspend fun saveHistoricalPrice(
        instrumentHistoricalPrice: InstrumentHistoricalPrice
    ): Unit = blockingTransaction {
        DAO.new {
            symbol = instrumentHistoricalPrice.symbol
            currency = instrumentHistoricalPrice.currency
            date = instrumentHistoricalPrice.date.toJavaLocalDate()
            price = instrumentHistoricalPrice.price
        }
    }

    private fun ResultRow.toModel() =
        InstrumentHistoricalPrice(
            symbol = this[Table.symbol],
            currency = this[Table.currency],
            date = this[Table.date].let {
                LocalDate(it.year, it.monthValue, it.dayOfMonth)
            },
            price = this[Table.price]
        )
}
