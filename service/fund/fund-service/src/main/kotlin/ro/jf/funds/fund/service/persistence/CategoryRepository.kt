package ro.jf.funds.fund.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.fund.api.model.CreateCategoryTO
import ro.jf.funds.fund.service.domain.Category
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.*

class CategoryRepository(
    private val database: Database,
) {
    object CategoryTable : UUIDTable("category") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
    }

    suspend fun list(userId: UUID): List<Category> = blockingTransaction {
        CategoryTable
            .selectAll()
            .where { CategoryTable.userId eq userId }
            .orderBy(CategoryTable.name)
            .map { it.toCategory() }
    }

    suspend fun findById(userId: UUID, categoryId: UUID): Category? = blockingTransaction {
        CategoryTable
            .selectAll()
            .where { (CategoryTable.userId eq userId) and (CategoryTable.id eq categoryId) }
            .singleOrNull()
            ?.toCategory()
    }

    suspend fun findByName(userId: UUID, name: String): Category? = blockingTransaction {
        CategoryTable
            .selectAll()
            .where { (CategoryTable.userId eq userId) and (CategoryTable.name eq name) }
            .singleOrNull()
            ?.toCategory()
    }

    suspend fun save(userId: UUID, request: CreateCategoryTO): Category = blockingTransaction {
        val result = CategoryTable.insert {
            it[CategoryTable.userId] = userId
            it[CategoryTable.name] = request.name
        }
        Category(
            id = result[CategoryTable.id].value,
            userId = result[CategoryTable.userId],
            name = result[CategoryTable.name],
        )
    }

    suspend fun deleteById(userId: UUID, categoryId: UUID): Unit = blockingTransaction {
        CategoryTable.deleteWhere { (CategoryTable.userId eq userId) and (CategoryTable.id eq categoryId) }
    }

    private fun ResultRow.toCategory() = Category(
        id = this[CategoryTable.id].value,
        userId = this[CategoryTable.userId],
        name = this[CategoryTable.name],
    )
}
