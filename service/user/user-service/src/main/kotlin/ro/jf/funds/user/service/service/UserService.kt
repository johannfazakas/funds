package ro.jf.funds.user.service.service

import ro.jf.funds.user.service.domain.CreateUserCommand
import ro.jf.funds.user.service.domain.User
import ro.jf.funds.user.service.domain.UserServiceException
import ro.jf.funds.user.service.persistence.UserRepository
import java.util.*

class UserService(
    private val userRepository: UserRepository
) {
    suspend fun listAll(): List<User> {
        return userRepository.listAll()
    }

    suspend fun findById(id: UUID): User {
        return userRepository.findById(id)
            ?: throw UserServiceException.UserNotFound(id)
    }

    suspend fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    suspend fun createUser(command: CreateUserCommand): User {
        val existingUser = userRepository.findByUsername(command.username)
        if (existingUser != null) {
            throw UserServiceException.UsernameAlreadyExists(command.username)
        }
        return userRepository.save(command)
    }

    suspend fun deleteById(id: UUID) {
        userRepository.deleteById(id)
    }
}
