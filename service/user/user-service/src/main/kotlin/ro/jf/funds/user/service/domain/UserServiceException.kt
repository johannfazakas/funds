package ro.jf.funds.user.service.domain

import java.util.*

sealed class UserServiceException(message: String) : RuntimeException(message) {
    class UserNotFound(val userId: UUID) : UserServiceException("User $userId not found")
    class UsernameAlreadyExists(val username: String) : UserServiceException("User with username '$username' already exists")
}
