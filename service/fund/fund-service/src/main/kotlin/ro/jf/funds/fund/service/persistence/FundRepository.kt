package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.service.domain.Fund
import java.util.*

class FundRepository(
    private val database: Database
) {
    object FundTable : UUIDTable("fund") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    suspend fun list(userId: UUID): List<Fund> = blockingTransaction {
        FundTable
            .selectAll()
            .where { FundTable.userId eq userId }
            .toFunds()
    }

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
