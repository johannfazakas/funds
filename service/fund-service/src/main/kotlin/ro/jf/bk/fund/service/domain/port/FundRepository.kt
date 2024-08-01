package ro.jf.bk.fund.service.domain.port

import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import java.util.*

interface FundRepository {
    suspend fun list(userId: UUID): List<Fund>
    suspend fun findById(userId: UUID, fundId: UUID): Fund?
    suspend fun findByName(userId: UUID, name: String): Fund?
    suspend fun save(command: CreateFundCommand): Fund
    suspend fun deleteById(userId: UUID, fundId: UUID)
}
