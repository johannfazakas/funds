package ro.jf.funds.account.api.exception

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.ProblemTO
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

private const val GENERIC_ACCOUNT_API_EXCEPTION = "Internal error"
private const val ACCOUNT_NAME_ALREADY_EXISTS = "Account name already exists"
private const val ACCOUNT_RECORD_CURRENCY_MISMATCH = "Currency mismatch between account and record"
private const val ACCOUNT_NOT_FOUND = "Account not found"

@Serializable
abstract class AccountApiException : ProblemTO() {

    @Serializable
    @SerialName(GENERIC_ACCOUNT_API_EXCEPTION)
    class Generic(override val detail: String? = null) : AccountApiException() {
        override val title = GENERIC_ACCOUNT_API_EXCEPTION
    }

    @Serializable
    @SerialName(ACCOUNT_NAME_ALREADY_EXISTS)
    class AccountNameAlreadyExists(val accountName: AccountName) : AccountApiException() {
        override val title = ACCOUNT_NAME_ALREADY_EXISTS
        override val detail = "Account with name $accountName already exists."
    }

    @Serializable
    @SerialName(ACCOUNT_NOT_FOUND)
    class AccountNotFound(
        @Serializable(with = UUIDSerializer::class)
        val accountId: UUID
    ) : AccountApiException() {
        override val title = ACCOUNT_NOT_FOUND
        override val detail = "Account with id $accountId not found"
    }

    @Serializable
    @SerialName(ACCOUNT_NOT_FOUND)
    class AccountRecordCurrencyMismatch(
        @Serializable(with = UUIDSerializer::class)
        val accountId: UUID,
        val accountName: AccountName,
        val accountUnit: FinancialUnit,
        val recordUnit: FinancialUnit
    ) : AccountApiException() {
        override val title = ACCOUNT_RECORD_CURRENCY_MISMATCH
        override val detail =
            "Currency mismatch on account $accountId with name $accountName and currency $accountUnit with record currency $recordUnit"
    }
}
