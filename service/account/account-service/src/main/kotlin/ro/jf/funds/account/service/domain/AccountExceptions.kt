package ro.jf.funds.account.service.domain

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.model.FinancialUnit
import java.util.*

sealed class AccountServiceException : RuntimeException() {
    class AccountNameAlreadyExists(val accountName: AccountName) : AccountServiceException()

    class AccountNotFound(val accountId: UUID) : AccountServiceException()

    class AccountNameNotFound(val accountName: AccountName) : AccountServiceException()

    class RecordAccountNotFound(val accountId: UUID) : AccountServiceException()

    class AccountRecordCurrencyMismatch(
        val accountId: UUID, val accountName: AccountName, val accountUnit: FinancialUnit, val recordUnit: FinancialUnit
    ) : AccountServiceException()
}
