package ro.jf.bk.account.api.exception

import ro.jf.bk.account.api.model.AccountName

sealed class AccountApiException(override val message: String) : RuntimeException(message) {
    class Generic(val reason: Any? = null) : AccountApiException("Internal error.")
    class AccountNameAlreadyExists(val accountName: AccountName) :
        AccountApiException("Account with name $accountName already exists.")
}
