package ro.jf.funds.user.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.user.api.model.UserTO

interface UserServiceApi {
    suspend fun listUsers(): List<UserTO>

    suspend fun findUserById(userId: Uuid): UserTO?

    suspend fun findUserByUsername(username: String): UserTO?

    suspend fun createUser(username: String): UserTO

    suspend fun deleteUserById(userId: Uuid)
}
