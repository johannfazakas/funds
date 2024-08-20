package ro.jf.bk.user.service.adapter.persistence

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.bk.commons.service.persistence.blockingTransaction
import ro.jf.bk.user.service.domain.command.CreateUserCommand
import ro.jf.bk.user.service.domain.model.User
import ro.jf.bk.user.service.domain.port.UserRepository
import java.util.*


class UserExposedRepository(
    private val database: Database
) : UserRepository {

    object Table : UUIDTable("user") {
        val username = varchar("username", 50)
    }

    class DAO(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<DAO>(Table)

        var username by Table.username
    }

    override suspend fun listAll(): List<User> = blockingTransaction {
        Table
            .selectAll()
            .map { it.toModel() }
    }

    override suspend fun findById(id: UUID): User? = blockingTransaction {
        Table
            .select { Table.id eq id }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun findByUsername(username: String): User? = blockingTransaction {
        Table
            .select { Table.username eq username }
            .map { it.toModel() }
            .singleOrNull()
    }

    override suspend fun save(command: CreateUserCommand): User = blockingTransaction {
        DAO.new { username = command.username }.toModel()
    }

    override suspend fun deleteById(id: UUID): Unit = blockingTransaction {
        Table.deleteWhere { Table.id eq id }
    }

    override suspend fun deleteAll(): Unit = blockingTransaction {
        Table.deleteAll()
    }

    private fun ResultRow.toModel() = User(id = this[Table.id].value, username = this[Table.username])

    private fun DAO.toModel() = User(id = id.value, username = username)
}
