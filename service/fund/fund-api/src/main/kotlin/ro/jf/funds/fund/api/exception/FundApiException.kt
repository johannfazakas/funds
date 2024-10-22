package ro.jf.funds.fund.api.exception

import java.util.*

sealed class FundApiException(override val message: String) : RuntimeException(message) {
    class Generic(val reason: Any? = null) : FundApiException("Internal error.")
    class AccountNotFound(val userId: UUID, val accountId: UUID) :
        FundApiException("Account not found for user $userId with id $accountId.")
}