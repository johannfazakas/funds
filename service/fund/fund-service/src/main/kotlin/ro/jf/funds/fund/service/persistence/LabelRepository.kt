package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.fund.api.model.CreateLabelTO
import ro.jf.funds.fund.service.domain.LabelDomain
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.*

class LabelRepository(
    private val database: Database,
) {
    object LabelTable : UUIDTable("label") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    suspend fun list(userId: UUID): List<LabelDomain> = blockingTransaction {
        LabelTable
            .selectAll()
            .where { LabelTable.userId eq userId }
            .orderBy(LabelTable.name)
            .map { it.toLabelDomain() }
    }

    suspend fun findById(userId: UUID, labelId: UUID): LabelDomain? = blockingTransaction {
        LabelTable
            .selectAll()
            .where { (LabelTable.userId eq userId) and (LabelTable.id eq labelId) }
            .singleOrNull()
            ?.toLabelDomain()
    }

    suspend fun findByName(userId: UUID, name: String): LabelDomain? = blockingTransaction {
        LabelTable
            .selectAll()
            .where { (LabelTable.userId eq userId) and (LabelTable.name eq name) }
            .singleOrNull()
            ?.toLabelDomain()
    }

    suspend fun save(userId: UUID, request: CreateLabelTO): LabelDomain = blockingTransaction {
        val result = LabelTable.insert {
            it[LabelTable.userId] = userId
            it[LabelTable.name] = request.name
        }
        LabelDomain(
            id = result[LabelTable.id].value,
            userId = result[LabelTable.userId],
            name = result[LabelTable.name],
        )
    }

    suspend fun deleteById(userId: UUID, labelId: UUID): Unit = blockingTransaction {
        LabelTable.deleteWhere { (LabelTable.userId eq userId) and (LabelTable.id eq labelId) }
    }

    private fun ResultRow.toLabelDomain() = LabelDomain(
        id = this[LabelTable.id].value,
        userId = this[LabelTable.userId],
        name = this[LabelTable.name],
    )
}
