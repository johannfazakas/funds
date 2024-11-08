package ro.jf.funds.account.api.event

import ro.jf.funds.commons.event.Domain
import ro.jf.funds.commons.event.EventType

val ACCOUNT_DOMAIN = Domain("account")

val CREATE_ACCOUNT_TRANSACTIONS_REQUEST = EventType("transactions-request")

val CREATE_ACCOUNT_TRANSACTIONS_RESPONSE = EventType("transactions-response")
