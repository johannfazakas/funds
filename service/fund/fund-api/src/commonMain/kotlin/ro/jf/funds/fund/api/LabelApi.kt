package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.CreateLabelTO
import ro.jf.funds.fund.api.model.LabelTO

interface LabelApi {
    suspend fun listLabels(userId: Uuid): List<LabelTO>
    suspend fun createLabel(userId: Uuid, request: CreateLabelTO): LabelTO
    suspend fun deleteLabelById(userId: Uuid, labelId: Uuid)
}
