package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundSortField
import ro.jf.funds.fund.api.model.UpdateFundTO
import ro.jf.funds.fund.service.domain.Fund
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import java.util.*

class FundService(
    private val fundRepository: FundRepository,
    private val recordRepository: RecordRepository,
) {
    suspend fun listFunds(
        userId: UUID,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<FundSortField>?,
    ): PagedResult<Fund> {
        return fundRepository.list(userId, pageRequest, sortRequest)
    }

    suspend fun findById(userId: UUID, fundId: UUID): Fund? {
        return fundRepository.findById(userId, fundId)
    }

    suspend fun findByName(userId: UUID, name: FundName): Fund? {
        return fundRepository.findByName(userId, name)
    }

    suspend fun createFund(userId: UUID, request: CreateFundTO): Fund {
        return fundRepository.save(userId, request)
    }

    suspend fun deleteFund(userId: UUID, fundId: UUID) {
        val records = recordRepository.list(userId, RecordFilter(fundId = fundId))
        if (records.items.isNotEmpty()) {
            throw FundServiceException.FundHasRecords(fundId)
        }
        fundRepository.deleteById(userId, fundId)
    }

    suspend fun updateFund(userId: UUID, fundId: UUID, request: UpdateFundTO): Fund {
        val existingFund = fundRepository.findById(userId, fundId)
            ?: throw FundServiceException.FundNotFound(fundId)

        val requestName = request.name
        val newName = requestName ?: existingFund.name

        if (requestName != null && requestName != existingFund.name) {
            val existingWithName = fundRepository.findByName(userId, requestName)
            if (existingWithName != null) {
                throw FundServiceException.FundNameAlreadyExists(requestName)
            }
        }

        return fundRepository.update(userId, fundId, newName)
            ?: throw FundServiceException.FundNotFound(fundId)
    }
}
