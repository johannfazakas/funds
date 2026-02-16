package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.RecordFilterTO
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.api.model.RecordTO
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest

interface RecordApi {
    suspend fun listRecords(
        userId: Uuid,
        filter: RecordFilterTO? = null,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<RecordSortField>? = null,
    ): PageTO<RecordTO>
}
