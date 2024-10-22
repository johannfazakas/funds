package ro.jf.funds.fund.service.domain.exception

import java.util.*

class AccountNotFoundException(val userId: UUID, val accountId: UUID) :
    RuntimeException("Account $accountId not found on user $userId.")