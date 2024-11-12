package ro.jf.funds.fund.api.event

import ro.jf.funds.commons.event.Domain
import ro.jf.funds.commons.event.EventType

val FUND_DOMAIN = Domain("fund")

val FUND_TRANSACTIONS_REQUEST = EventType("transactions-request")

val FUND_TRANSACTIONS_RESPONSE = EventType("transactions-response")
