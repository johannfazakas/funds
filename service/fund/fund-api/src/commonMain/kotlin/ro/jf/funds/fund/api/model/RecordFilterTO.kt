package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid

data class RecordFilterTO(
    val accountId: Uuid? = null,
    val fundId: Uuid? = null,
    val unit: String? = null,
    val label: String? = null,
)
