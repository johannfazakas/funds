package ro.jf.bk.fund.service.domain.service

import ro.jf.bk.fund.service.domain.command.CreateFundCommand
import ro.jf.bk.fund.service.domain.model.Fund
import ro.jf.bk.fund.service.domain.port.AccountRepository
import ro.jf.bk.fund.service.domain.port.FundRepository
import ro.jf.bk.fund.service.domain.port.FundService
import java.util.*

class FundServiceImpl(
    private val fundRepository: FundRepository,
    private val accountRepository: AccountRepository
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
        validateAccountsExist(command.userId, command.accounts.map { it.accountId })
        return fundRepository.save(command)
    }

    override suspend fun deleteFund(userId: UUID, fundId: UUID) {
        return fundRepository.deleteById(userId, fundId)
    }

    private suspend fun validateAccountsExist(userId: UUID, accountIds: List<UUID>) {
        accountIds.forEach { validateAccountExist(userId, it) }
    }

    private suspend fun validateAccountExist(userId: UUID, accountId: UUID) {
        accountRepository.findById(userId, accountId)
            ?: throw IllegalArgumentException("Account with id $accountId not found")
    }
}
