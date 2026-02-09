package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.platform.jvm.persistence.toExposedSortOrder
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundSortField
import ro.jf.funds.fund.service.domain.Fund
import java.util.*

class FundRepository(
    private val database: Database,
) {
    object FundTable : UUIDTable("fund") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    suspend fun list(
        userId: UUID,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<FundSortField>?,
    ): PagedResult<Fund> = blockingTransaction {
        val baseQuery = FundTable.selectAll().where { FundTable.userId eq userId }
        val total = baseQuery.count()

        val funds = FundTable.selectAll()
            .where { FundTable.userId eq userId }
            .applySorting(sortRequest)
            .applyPagination(pageRequest)
            .toFunds()

        PagedResult(funds, total)
    }

    private fun Query.applySorting(sortRequest: SortRequest<FundSortField>?): Query =
        sortRequest?.let {
            val sortColumn = when (it.field) {
                FundSortField.NAME -> FundTable.name
            }
            orderBy(sortColumn to it.order.toExposedSortOrder())
        } ?: this

    private fun Query.applyPagination(pageRequest: PageRequest?): Query =
        pageRequest?.let { limit(it.limit).offset(it.offset.toLong()) } ?: this

    suspend fun findById(userId: UUID, fundId: UUID): Fund? = blockingTransaction {
        FundTable
            .selectAll()
            .where { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
            .toFunds()
            .singleOrNull()
    }

    suspend fun findByName(userId: UUID, name: FundName): Fund? = blockingTransaction {
        FundTable
            .selectAll()
            .where { (FundTable.userId eq userId) and (FundTable.name eq name.value) }
            .toFunds()
            .singleOrNull()
    }

    suspend fun save(userId: UUID, request: CreateFundTO): Fund = blockingTransaction {
        val fund = FundTable.insert {
            it[FundTable.userId] = userId
            it[FundTable.name] = request.name.value
        }
        fund.let {
            Fund(
                id = it[FundTable.id].value,
                userId = it[FundTable.userId],
                name = FundName(it[FundTable.name]),
            )
        }
    }

    suspend fun deleteById(userId: UUID, fundId: UUID): Unit = blockingTransaction {
        FundTable.deleteWhere { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
    }

    private fun Query.toFunds(): List<Fund> = this
        .groupBy { it[FundTable.id].value }
        .map { (_, rows) -> rows.toFund() }

    private fun List<ResultRow>.toFund() = Fund(
        id = this.first()[FundTable.id].value,
        userId = this.first()[FundTable.userId],
        name = FundName(this.first()[FundTable.name]),
    )
}
