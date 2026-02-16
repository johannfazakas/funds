package ro.jf.funds.fund.service.domain

import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.FundName
import java.util.*

sealed class FundServiceException : RuntimeException() {
    class FundNotFound(val fundId: UUID) : FundServiceException()
    class FundNameAlreadyExists(val fundName: FundName) : FundServiceException()
    class FundHasRecords(val fundId: UUID) : FundServiceException()
    class AccountHasRecords(val accountId: UUID) : FundServiceException()
    class TransactionNotFound(val transactionId: UUID) : FundServiceException()
    class TransactionFundNotFound(val fundId: UUID) : FundServiceException()
    class AccountNameAlreadyExists(val accountName: AccountName) : FundServiceException()
    class AccountNotFound(val accountId: UUID) : FundServiceException()
    class AccountNameNotFound(val accountName: AccountName) : FundServiceException()
    class RecordAccountNotFound(val accountId: UUID) : FundServiceException()
    class RecordFundNotFound(val fundId: UUID) : FundServiceException()
    class AccountRecordCurrencyMismatch(
        val accountId: UUID,
        val accountName: AccountName,
        val accountUnit: FinancialUnit,
        val recordUnit: FinancialUnit
    ) : FundServiceException()
}
