package ro.jf.funds.fund.service.domain

import java.util.*

sealed class FundServiceException : RuntimeException() {
    class FundNotFound(val fundId: UUID) : FundServiceException()
    class TransactionFundNotFound(val fundId: UUID) : FundServiceException()
}
