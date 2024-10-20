package ro.jf.bk.account.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.account.service.domain.Account
import ro.jf.bk.commons.service.persistence.blockingTransaction
import java.util.*

class AccountRepository(
    private val database: Database
) {

    object AccountTable : UUIDTable("account") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val type = varchar("type", 50)
        val currency = varchar("currency", 50)
        val symbol = varchar("symbol", 50).nullable()
    }

    suspend fun list(userId: UUID): List<Account> = blockingTransaction {
        AccountTable
            .select { AccountTable.userId eq userId }
            .map { it.toModel() }
    }

    suspend fun findById(userId: UUID, accountId: UUID): Account? = blockingTransaction {
        AccountTable
            .select { (AccountTable.userId eq userId) and (AccountTable.id eq accountId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun findByName(userId: UUID, name: AccountName): Account? = blockingTransaction {
        AccountTable
            .select { (AccountTable.userId eq userId) and (AccountTable.name eq name.value) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun save(userId: UUID, command: CreateCurrencyAccountTO): Account.Currency = blockingTransaction {
        AccountTable.insert {
            it[type] = "currency"
            it[AccountTable.userId] = userId
            it[name] = command.name.value
            it[currency] = command.currency
        }.let {
            Account.Currency(
                id = it[AccountTable.id].value,
                userId = it[AccountTable.userId],
                name = AccountName(it[AccountTable.name]),
                currency = it[AccountTable.currency]
            )
        }
    }

    suspend fun save(userId: UUID, command: CreateInstrumentAccountTO): Account.Instrument = blockingTransaction {
        AccountTable.insert {
            it[type] = "instrument"
            it[AccountTable.userId] = userId
            it[name] = command.name.value
            it[currency] = command.currency
            it[symbol] = command.symbol
        }.let {
            Account.Instrument(
                id = it[AccountTable.id].value,
                userId = it[AccountTable.userId],
                name = AccountName(it[AccountTable.name]),
                currency = it[AccountTable.currency],
                symbol = it[AccountTable.symbol] ?: error("Symbol is missing")
            )
        }
    }

    suspend fun deleteById(userId: UUID, accountId: UUID): Unit = blockingTransaction {
        AccountTable.deleteWhere { (AccountTable.userId eq userId) and (id eq accountId) }
    }

    suspend fun deleteAllByUserId(userId: UUID): Unit = blockingTransaction {
        AccountTable.deleteWhere { AccountTable.userId eq userId }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        AccountTable.deleteAll()
    }

    private fun ResultRow.toModel() =
        when (this[AccountTable.type]) {
            "currency" -> Account.Currency(
                id = this[AccountTable.id].value,
                userId = this[AccountTable.userId],
                name = AccountName(this[AccountTable.name]),
                currency = this[AccountTable.currency],
            )

            "instrument" -> Account.Instrument(
                id = this[AccountTable.id].value,
                userId = this[AccountTable.userId],
                name = AccountName(this[AccountTable.name]),
                currency = this[AccountTable.currency],
                symbol = this[AccountTable.symbol] ?: error("Symbol is missing")
            )

            else -> error("Unknown account type")
        }
}
