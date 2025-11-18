package ro.jf.funds.user.api.exception

sealed class UserApiException(override val message: String) : RuntimeException(message) {
    class Generic : UserApiException("Internal error.")
    class UsernameAlreadyExists(val username: String) : UserApiException("User with username $username already exists.")
}
