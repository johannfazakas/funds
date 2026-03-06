package ro.jf.funds.importer.service.service.conversion

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.LabelTO
import ro.jf.funds.fund.sdk.LabelSdk
import ro.jf.funds.importer.service.domain.Store

class LabelService(
    private val labelSdk: LabelSdk,
) {
    suspend fun getLabelStore(userId: Uuid): Store<String, LabelTO> = labelSdk
        .listLabels(userId)
        .associateBy { it.name }
        .let { Store(it) }
}
