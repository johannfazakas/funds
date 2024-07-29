package ro.jf.bk.account.service.adapter.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.bk.account.service.domain.command.CreateCurrencyAccountCommand
import ro.jf.bk.account.service.domain.command.CreateInstrumentAccountCommand
import ro.jf.bk.account.service.domain.model.Account
import ro.jf.bk.account.service.domain.port.AccountRepository
import ro.jf.bk.commons.service.persistence.exposed.blockingTransaction
import java.util.*

class AccountExposedRepository(
    private val database: Database
) : AccountRepository {

    object AccountTable : UUIDTable("account") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val type = varchar("type", 50)
        val currency = varchar("currency", 50)
        val symbol = varchar("symbol", 50).nullable()
    }

    override suspend fun list(userId: UUID): List<Account> = blockingTransaction {
        AccountTable
            .select { AccountTable.userId eq userId }
            .map { it.toModel() }
    }

    override suspend fun findById(userId: UUID, accountId: UUID): Account? = blockingTransaction {
        AccountTable
            .select { (AccountTable.userId eq userId) and (AccountTable.id eq accountId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun findByName(userId: UUID, name: String): Account? = blockingTransaction {
        AccountTable
            .select { (AccountTable.userId eq userId) and (AccountTable.name eq name) }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun save(command: CreateCurrencyAccountCommand): Account.Currency = blockingTransaction {
        AccountTable.insert {
            it[type] = "currency"
            it[userId] = command.userId
            it[name] = command.name
            it[currency] = command.currency
        }.let {
            Account.Currency(
                id = it[AccountTable.id].value,
                userId = it[AccountTable.userId],
                name = it[AccountTable.name],
                currency = it[AccountTable.currency]
            )
        }
    }

    override suspend fun save(command: CreateInstrumentAccountCommand): Account.Instrument = blockingTransaction {
        AccountTable.insert {
            it[type] = "instrument"
            it[userId] = command.userId
            it[name] = command.name
            it[currency] = command.currency
            it[symbol] = command.symbol
        }.let {
            Account.Instrument(
                id = it[AccountTable.id].value,
                userId = it[AccountTable.userId],
                name = it[AccountTable.name],
                currency = it[AccountTable.currency],
                symbol = it[AccountTable.symbol] ?: error("Symbol is missing")
            )
        }
    }

    override suspend fun deleteById(userId: UUID, accountId: UUID): Unit = blockingTransaction {
        AccountTable.deleteWhere { (AccountTable.userId eq userId) and (id eq accountId) }
    }

    override suspend fun deleteAllByUserId(userId: UUID): Unit = blockingTransaction {
        AccountTable.deleteWhere { AccountTable.userId eq userId }
    }

    override suspend fun deleteAll(): Unit = blockingTransaction {
        AccountTable.deleteAll()
    }

    private fun ResultRow.toModel() =
        when (this[AccountTable.type]) {
            "currency" -> Account.Currency(
                id = this[AccountTable.id].value,
                userId = this[AccountTable.userId],
                name = this[AccountTable.name],
                currency = this[AccountTable.currency],
            )

            "instrument" -> Account.Instrument(
                id = this[AccountTable.id].value,
                userId = this[AccountTable.userId],
                name = this[AccountTable.name],
                currency = this[AccountTable.currency],
                symbol = this[AccountTable.symbol] ?: error("Symbol is missing")
            )

            else -> error("Unknown account type")
        }
}
