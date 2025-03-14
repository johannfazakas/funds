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
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyPairHistoricalPriceRepository
import java.util.*

// TODO(Johann) why are there 2 repositories?
class CurrencyPairHistoricalPriceExposedRepository(
    private val database: Database,
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
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
    ): CurrencyPairHistoricalPrice? = blockingTransaction {
        Table
            .selectAll()
            .where {
                (Table.sourceCurrency eq sourceCurrency.value) and
                        (Table.targetCurrency eq targetCurrency.value) and
                        (Table.date eq date.toJavaLocalDate())
            }
            .mapNotNull { it.toModel() }
            .singleOrNull()
    }

    override suspend fun getHistoricalPrices(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<CurrencyPairHistoricalPrice> = blockingTransaction {
        Table
            .selectAll()
            .where {
                (Table.sourceCurrency eq sourceCurrency.value) and
                        (Table.targetCurrency eq targetCurrency.value) and
                        (Table.date inList dates.map { it.toJavaLocalDate() })
            }
            .map { it.toModel() }
    }


    override suspend fun saveHistoricalPrice(
        currencyPairHistoricalPrice: CurrencyPairHistoricalPrice,
    ): Unit = blockingTransaction {
        DAO.new {
            sourceCurrency = currencyPairHistoricalPrice.sourceCurrency.value
            targetCurrency = currencyPairHistoricalPrice.targetCurrency.value
            date = currencyPairHistoricalPrice.date.toJavaLocalDate()
            price = currencyPairHistoricalPrice.price
        }
    }

    private fun ResultRow.toModel() =
        CurrencyPairHistoricalPrice(
            sourceCurrency = Currency(this[Table.sourceCurrency]),
            targetCurrency = Currency(this[Table.targetCurrency]),
            date = this[Table.date].let {
                LocalDate(it.year, it.monthValue, it.dayOfMonth)
            },
            price = this[Table.price]
        )
}