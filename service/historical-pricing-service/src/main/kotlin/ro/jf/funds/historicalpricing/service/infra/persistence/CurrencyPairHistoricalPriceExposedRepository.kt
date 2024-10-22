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
import ro.jf.funds.historicalpricing.service.domain.model.CurrencyPairHistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyPairHistoricalPriceRepository
import java.util.*


class CurrencyPairHistoricalPriceExposedRepository(
    private val database: Database
) : CurrencyPairHistoricalPriceRepository {

    object Table : UUIDTable("currency_pair_historical_price") {
        val sourceCurrency = varchar("source_currency", 50)
        val targetCurrency = varchar("target_currency", 50)
        val date = date("date")
        val price = decimal("price", 20, 8)
    }

    class DAO(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<DAO>(Table)

        var sourceCurrency by Table.sourceCurrency
        var targetCurrency by Table.targetCurrency
        var date by Table.date
        var price by Table.price
    }

    override suspend fun getHistoricalPrice(
        sourceCurrency: String,
        targetCurrency: String,
        date: LocalDate
    ): CurrencyPairHistoricalPrice? = blockingTransaction {
        Table
            .select {
                (Table.sourceCurrency eq sourceCurrency) and
                        (Table.targetCurrency eq targetCurrency) and
                        (Table.date eq date.toJavaLocalDate())
            }
            .mapNotNull { it.toModel() }
            .singleOrNull()
    }

    override suspend fun getHistoricalPrices(
        sourceCurrency: String,
        targetCurrency: String,
        dates: List<LocalDate>
    ): List<CurrencyPairHistoricalPrice> = blockingTransaction {
        Table
            .select {
                (Table.sourceCurrency eq sourceCurrency) and
                        (Table.targetCurrency eq targetCurrency) and
                        (Table.date inList dates.map { it.toJavaLocalDate() })
            }
            .map { it.toModel() }
    }


    override suspend fun saveHistoricalPrice(
        currencyPairHistoricalPrice: CurrencyPairHistoricalPrice
    ): Unit = blockingTransaction {
        DAO.new {
            sourceCurrency = currencyPairHistoricalPrice.sourceCurrency
            targetCurrency = currencyPairHistoricalPrice.targetCurrency
            date = currencyPairHistoricalPrice.date.toJavaLocalDate()
            price = currencyPairHistoricalPrice.price
        }
    }

    private fun ResultRow.toModel() =
        CurrencyPairHistoricalPrice(
            sourceCurrency = this[Table.sourceCurrency],
            targetCurrency = this[Table.targetCurrency],
            date = this[Table.date].let {
                LocalDate(it.year, it.monthValue, it.dayOfMonth)
            },
            price = this[Table.price]
        )
}