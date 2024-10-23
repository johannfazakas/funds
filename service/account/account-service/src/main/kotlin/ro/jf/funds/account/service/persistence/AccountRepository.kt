package ro.jf.funds.account.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.CreateAccountTO
import ro.jf.funds.account.service.domain.Account
import ro.jf.funds.account.service.persistence.AccountTransactionRepository.AccountRecordTable
import ro.jf.funds.commons.service.persistence.blockingTransaction
import java.util.*

class AccountRepository(
    private val database: Database
) {
    object AccountTable : UUIDTable("account") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
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

    suspend fun save(userId: UUID, command: CreateAccountTO): Account = blockingTransaction {
        AccountTable.insert {
            it[AccountTable.userId] = userId
            it[name] = command.name.value
            it[unitType] = command.unit.toUnitType()
            it[unit] = command.unit.value
        }.let {
            Account(
                id = it[AccountTable.id].value,
                userId = it[AccountTable.userId],
                name = AccountName(it[AccountTable.name]),
                unit = toFinancialUnit(it[AccountTable.unitType], it[AccountTable.unit]),
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
        Account(
            id = this[AccountTable.id].value,
            userId = this[AccountTable.userId],
            name = AccountName(this[AccountTable.name]),
            unit = toFinancialUnit(this[AccountTable.unitType], this[AccountTable.unit]),
        )
}
