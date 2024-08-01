package ro.jf.bk.fund.service.domain.service

import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import ro.jf.bk.fund.service.domain.port.FundRepository
import ro.jf.bk.fund.service.domain.port.FundService
import java.util.*

class FundServiceImpl(
    private val fundRepository: FundRepository
) : FundService {
    override suspend fun listFunds(userId: UUID): List<Fund> {
        return fundRepository.list(userId)
    }

    override suspend fun findById(userId: UUID, fundId: UUID): Fund? {
        return fundRepository.findById(userId, fundId)
    }

    override suspend fun findByName(userId: UUID, name: String): Fund? {
        return fundRepository.findByName(userId, name)
    }

    override suspend fun createFund(command: CreateFundCommand): Fund {
        return fundRepository.save(command)
    }

    override suspend fun deleteFund(userId: UUID, fundId: UUID) {
        return fundRepository.deleteById(userId, fundId)
    }
}
