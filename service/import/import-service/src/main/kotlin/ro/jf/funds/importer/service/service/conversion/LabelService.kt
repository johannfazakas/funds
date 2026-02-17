package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.fund.api.model.LabelTO
import ro.jf.funds.fund.sdk.LabelSdk
import ro.jf.funds.importer.service.domain.Store
import java.util.*

class LabelService(
    private val labelSdk: LabelSdk,
) {
    suspend fun getLabelStore(userId: UUID): Store<String, LabelTO> = labelSdk
        .listLabels(com.benasher44.uuid.Uuid.fromString(userId.toString()))
        .associateBy { it.name }
        .let { Store(it) }
}
