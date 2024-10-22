package ro.jf.bk.fund.service.service

import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.service.domain.Fund
import ro.jf.bk.fund.service.persistence.FundRepository
import java.util.*

class FundService(
    private val fundRepository: FundRepository,
    private val accountSdkAdapter: AccountSdkAdapter
) {
    suspend fun listFunds(userId: UUID): List<Fund> {
        return fundRepository.list(userId)
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
        return fundRepository.deleteById(userId, fundId)
    }
}
