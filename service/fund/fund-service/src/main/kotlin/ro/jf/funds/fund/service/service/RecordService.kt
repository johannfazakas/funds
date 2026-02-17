package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.service.domain.Record
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.RecordRepository
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import java.util.*

class RecordService(
    private val recordRepository: RecordRepository,
) {
    suspend fun listFinancialUnits(userId: UUID): List<FinancialUnit> {
        return recordRepository.listDistinctFinancialUnits(userId)
    }

    suspend fun listRecords(
        userId: UUID,
        filter: RecordFilter?,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<RecordSortField>?,
    ): PagedResult<Record> {
        return recordRepository.list(userId, filter, pageRequest, sortRequest)
    }
}
