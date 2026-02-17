package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.CreateLabelTO
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.LabelDomain
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.LabelRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import java.util.*

private val LABEL_PATTERN = Regex("^[a-zA-Z0-9_]+$")

class LabelService(
    private val labelRepository: LabelRepository,
    private val recordRepository: RecordRepository,
) {
    suspend fun listLabels(userId: UUID): List<LabelDomain> {
        return labelRepository.list(userId)
    }

    suspend fun createLabel(userId: UUID, request: CreateLabelTO): LabelDomain {
        require(request.name.isNotBlank()) { "Label name must not be blank" }
        require(request.name.matches(LABEL_PATTERN)) { "Label name must contain only letters, numbers or underscore" }
        val existing = labelRepository.findByName(userId, request.name)
        if (existing != null) {
            throw FundServiceException.LabelNameAlreadyExists(request.name)
        }
        return labelRepository.save(userId, request)
    }

    suspend fun deleteLabel(userId: UUID, labelId: UUID) {
        val label = labelRepository.findById(userId, labelId)
            ?: throw FundServiceException.LabelNotFound(labelId)
        val records = recordRepository.list(userId, RecordFilter(label = label.name))
        if (records.items.isNotEmpty()) {
            throw FundServiceException.LabelHasRecords(labelId)
        }
        labelRepository.deleteById(userId, labelId)
    }
}
