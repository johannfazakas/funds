package ro.jf.bk.fund.service.adapter.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.bk.commons.service.persistence.blockingTransaction
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import ro.jf.bk.fund.service.domain.model.FundAccount
import ro.jf.bk.fund.service.domain.port.FundRepository
import java.util.*

class FundExposedRepository(
    private val database: Database
) : FundRepository {
    object FundTable : UUIDTable("fund") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    object FundAccountTable : UUIDTable("fund_account") {
        val userId = uuid("user_id")
        val fundId = uuid("fund_id").references(FundTable.id)
        val accountId = uuid("account_id")
    }

    override suspend fun list(userId: UUID): List<Fund> = blockingTransaction {
        (FundTable leftJoin FundAccountTable)
            .select { FundTable.userId eq userId }
            .toFunds()
    }

    override suspend fun findById(userId: UUID, fundId: UUID): Fund? = blockingTransaction {
        (FundTable leftJoin FundAccountTable)
            .select { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
            .toFunds()
            .singleOrNull()
    }

    override suspend fun findByName(userId: UUID, name: FundName): Fund? = blockingTransaction {
        FundTable
            .select { (FundTable.userId eq userId) and (FundTable.name eq name.value) }
            .toFunds()
            .singleOrNull()
    }

    override suspend fun save(command: CreateFundCommand): Fund = blockingTransaction {
        val fund = FundTable.insert {
            it[userId] = command.userId
            it[name] = command.name.value
        }
        val accounts = command.accounts.map { account ->
            FundAccountTable.insert {
                it[fundId] = fund[FundTable.id].value
                it[userId] = command.userId
                it[accountId] = account.accountId
            }
        }
        fund.let {
            Fund(
                id = it[FundTable.id].value,
                userId = it[FundTable.userId],
                name = FundName(it[FundTable.name]),
                accounts = accounts.map {
                    FundAccount(
                        id = it[FundAccountTable.accountId]
                    )
                }
            )
        }
    }

    override suspend fun deleteById(userId: UUID, fundId: UUID): Unit = blockingTransaction {
        FundTable.deleteWhere { (FundTable.userId eq userId) and (FundTable.id eq fundId) }
    }

    private fun Query.toFunds(): List<Fund> = this
        .groupBy { it[FundTable.id].value }
        .map { (_, rows) -> rows.toFund() }

    private fun List<ResultRow>.toFund() = Fund(
        id = this.first()[FundTable.id].value,
        userId = this.first()[FundTable.userId],
        name = FundName(this.first()[FundTable.name]),
        accounts = this.mapNotNull { it.toFundAccount() }
    )

    private fun ResultRow.toFundAccount(): FundAccount? =
        if (this.hasValue(FundAccountTable.id)) {
            FundAccount(
                id = this[FundAccountTable.accountId]
            )
        } else {
            null
        }
}
