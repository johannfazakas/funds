package ro.jf.funds.fund.api.event

import ro.jf.funds.platform.api.event.EventType

object FundEvents {
    private const val FUND_DOMAIN = "fund"
    private const val TRANSACTION_RESOURCE = "transaction"

    private const val BATCH_REQUEST = "batch-create-request"
    private const val BATCH_RESPONSE = "batch-create-response"
    private const val CREATED = "created"

    val FundTransactionsBatchRequest = EventType(FUND_DOMAIN, TRANSACTION_RESOURCE, BATCH_REQUEST)
    val FundTransactionsBatchResponse = EventType(FUND_DOMAIN, TRANSACTION_RESOURCE, BATCH_RESPONSE)
    val FundTransactionsCreated = EventType(FUND_DOMAIN, TRANSACTION_RESOURCE, CREATED)
}
