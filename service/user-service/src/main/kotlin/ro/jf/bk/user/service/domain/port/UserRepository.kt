package ro.jf.bk.user.service.domain.port

import ro.jf.bk.user.service.domain.command.CreateUserCommand
import ro.jf.bk.user.service.domain.model.User
import java.util.*

interface UserRepository {
    suspend fun listAll(): List<User>
    suspend fun findById(id: UUID): User?
    suspend fun findByUsername(username: String): User?
    suspend fun save(command: CreateUserCommand): User
    suspend fun deleteById(id: UUID)
    suspend fun deleteAll()
}
