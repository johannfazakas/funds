package ro.jf.funds.fund.service.domain

import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.commons.model.FinancialUnit
import java.util.*

sealed class FundServiceException : RuntimeException() {
    class FundNotFound(val fundId: UUID) : FundServiceException()
    class TransactionFundNotFound(val fundId: UUID) : FundServiceException()
    class AccountNameAlreadyExists(val accountName: AccountName) : FundServiceException()
    class AccountNotFound(val accountId: UUID) : FundServiceException()
    class AccountNameNotFound(val accountName: AccountName) : FundServiceException()
    class RecordAccountNotFound(val accountId: UUID) : FundServiceException()
    class AccountRecordCurrencyMismatch(
        val accountId: UUID,
        val accountName: AccountName,
        val accountUnit: FinancialUnit,
        val recordUnit: FinancialUnit
    ) : FundServiceException()
}
