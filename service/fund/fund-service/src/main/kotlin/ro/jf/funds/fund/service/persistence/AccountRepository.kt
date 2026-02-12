package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.api.model.toFinancialUnit
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.platform.jvm.persistence.toExposedSortOrder
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountSortField
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.api.model.UpdateAccountTO
import ro.jf.funds.fund.service.domain.Account
import java.util.*

class AccountRepository(
    private val database: Database,
) {
    object AccountTable : UUIDTable("account") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val unitType = varchar("unit_type", 50)
        val unit = varchar("unit", 50)
    }

    suspend fun list(
        userId: UUID,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<AccountSortField>?,
    ): PagedResult<Account> = blockingTransaction {
        val baseQuery = AccountTable.selectAll().where { AccountTable.userId eq userId }
        val total = baseQuery.count()

        val accounts = AccountTable.selectAll()
            .where { AccountTable.userId eq userId }
            .applySorting(sortRequest)
            .applyPagination(pageRequest)
            .map { it.toModel() }

        PagedResult(accounts, total)
    }

    private fun Query.applySorting(sortRequest: SortRequest<AccountSortField>?): Query =
        sortRequest?.let {
            val sortColumn = when (it.field) {
                AccountSortField.NAME -> AccountTable.name
                AccountSortField.UNIT -> AccountTable.unit
            }
            orderBy(sortColumn to it.order.toExposedSortOrder())
        } ?: this

    private fun Query.applyPagination(pageRequest: PageRequest?): Query =
        pageRequest?.let { limit(it.limit).offset(it.offset.toLong()) } ?: this

    suspend fun findById(userId: UUID, accountId: UUID): Account? = blockingTransaction {
        AccountTable
            .selectAll()
            .where { (AccountTable.userId eq userId) and (AccountTable.id eq accountId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun findByName(userId: UUID, name: AccountName): Account? = blockingTransaction {
        AccountTable
            .selectAll()
            .where { (AccountTable.userId eq userId) and (AccountTable.name eq name.value) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun save(userId: UUID, command: CreateAccountTO): Account = blockingTransaction {
        AccountTable.insert {
            it[AccountTable.userId] = userId
            it[name] = command.name.value
            it[unitType] = command.unit.type.value
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

    suspend fun update(userId: UUID, accountId: UUID, request: UpdateAccountTO): Account? =
        blockingTransaction {
            if (request.name == null && request.unit == null) {
                return@blockingTransaction findById(userId, accountId)
            }
            val updated =
                AccountTable.update({ (AccountTable.userId eq userId) and (AccountTable.id eq accountId) }) {
                    request.name?.let { name -> it[AccountTable.name] = name.value }
                    request.unit?.let { unit ->
                        it[unitType] = unit.type.value
                        it[AccountTable.unit] = unit.value
                    }
                }
            if (updated > 0) {
                AccountTable.selectAll()
                    .where { (AccountTable.userId eq userId) and (AccountTable.id eq accountId) }
                    .map { it.toModel() }
                    .singleOrNull()
            } else {
                null
            }
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