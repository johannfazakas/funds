package ro.jf.bk.fund.service.domain.port

import ro.jf.bk.fund.service.domain.model.Account
import java.util.*

interface AccountRepository {
    suspend fun findById(userId: UUID, accountId: UUID): Account?
}
