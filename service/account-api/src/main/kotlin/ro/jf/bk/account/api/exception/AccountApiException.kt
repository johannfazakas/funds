package ro.jf.bk.account.api.exception

sealed class AccountApiException(override val message: String) : RuntimeException(message) {
    class Generic(val reason: Any? = null) : AccountApiException("Internal error.")
    class AccountNameAlreadyExists(val accountName: String) :
        AccountApiException("Account with name $accountName already exists.")
}
