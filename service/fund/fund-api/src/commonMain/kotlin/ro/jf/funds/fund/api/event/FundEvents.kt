package ro.jf.funds.fund.api.event

import ro.jf.funds.commons.api.event.EventType

object FundEvents {
    private const val FUND_DOMAIN = "fund"
    private const val TRANSACTION_RESOURCE = "transaction"
    private const val BATCH_REQUEST = "batch-create-request"
    private const val BATCH_RESPONSE = "batch-create-response"

    val FundTransactionsBatchRequest = EventType(FUND_DOMAIN, TRANSACTION_RESOURCE, BATCH_REQUEST)
    val FundTransactionsBatchResponse = EventType(FUND_DOMAIN, TRANSACTION_RESOURCE, BATCH_RESPONSE)
}
