package ro.jf.bk.user.api

import ro.jf.bk.user.api.model.UserTO
import java.util.*

interface UserServiceApi {
    suspend fun listUsers(): List<UserTO>

    suspend fun findUserById(userId: UUID): UserTO?

    suspend fun findUserByUsername(username: String): UserTO?

    suspend fun createUser(username: String): UserTO

    suspend fun deleteUserById(userId: UUID)
}