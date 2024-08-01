package ro.jf.bk.fund.service.domain.port

import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import java.util.*

interface FundService {
    suspend fun listFunds(userId: UUID): List<Fund>
    suspend fun findById(userId: UUID, fundId: UUID): Fund?
    suspend fun findByName(userId: UUID, name: String): Fund?
    suspend fun createFund(command: CreateFundCommand): Fund
    suspend fun deleteFund(userId: UUID, fundId: UUID)
}
