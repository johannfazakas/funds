package ro.jf.bk.fund.service.adapter.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.bk.commons.service.persistence.exposed.blockingTransaction
import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.fund.Fund
import ro.jf.bk.fund.service.domain.port.FundRepository
import java.util.*

class FundExposedRepository(
    private val database: Database
) : FundRepository {
    object FundTable : UUIDTable("fund") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    override suspend fun list(userId: UUID): List<Fund> = blockingTransaction {
        FundTable
            .select { FundTable.userId eq userId }
            .map { it.toModel() }
    }

    override suspend fun findById(userId: UUID, fundId: UUID): Fund? = blockingTransaction {
        FundTable
            .select { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun findByName(userId: UUID, name: String): Fund? = blockingTransaction {
        FundTable
            .select { (FundTable.userId eq userId) and (FundTable.name eq name) }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun save(command: CreateFundCommand): Fund = blockingTransaction {
        FundTable.insert {
            it[userId] = command.userId
            it[name] = command.name
        }.let {
            Fund(
                id = it[FundTable.id].value,
                userId = it[FundTable.userId],
                name = it[FundTable.name],
            )
        }
    }

    override suspend fun deleteById(userId: UUID, fundId: UUID): Unit = blockingTransaction {
        FundTable.deleteWhere { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
    }

    private fun ResultRow.toModel(): Fund {
        return Fund(
            id = this[FundTable.id].value,
            userId = this[FundTable.userId],
            name = this[FundTable.name]
        )
    }
}
