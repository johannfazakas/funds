package ro.jf.funds.user.service.domain

import java.util.*

sealed class UserException(message: String) : RuntimeException(message) {
    class UserNotFound(val userId: UUID) : UserException("User $userId not found")
}
