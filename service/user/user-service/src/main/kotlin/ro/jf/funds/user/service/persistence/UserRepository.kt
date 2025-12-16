package ro.jf.funds.user.service.persistence

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.user.service.domain.CreateUserCommand
import ro.jf.funds.user.service.domain.User
import java.util.*


class UserRepository(
    private val database: Database
) {

    object Table : UUIDTable("user") {
        val username = varchar("username", 50)
    }

    class DAO(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<DAO>(Table)

        var username by Table.username
    }

    suspend fun listAll(): List<User> = blockingTransaction {
        Table
            .selectAll()
            .map { it.toModel() }
    }

    suspend fun findById(id: UUID): User? = blockingTransaction {
        Table
            .selectAll()
            .where { Table.id eq id }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun findByUsername(username: String): User? = blockingTransaction {
        Table
            .selectAll()
            .where { Table.username eq username }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun save(command: CreateUserCommand): User = blockingTransaction {
        DAO.new { username = command.username }.toModel()
    }

    suspend fun deleteById(id: UUID): Unit = blockingTransaction {
        Table.deleteWhere { Table.id eq id }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        Table.deleteAll()
    }

    private fun ResultRow.toModel() = User(id = this[Table.id].value, username = this[Table.username])

    private fun DAO.toModel() = User(id = id.value, username = username)
}
