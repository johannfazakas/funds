package ro.jf.bk.fund.service.service

import ro.jf.bk.account.api.model.AccountRecordTO
import ro.jf.bk.account.api.model.AccountTransactionTO
import ro.jf.bk.account.api.model.CreateAccountRecordTO
import ro.jf.bk.account.api.model.CreateAccountTransactionTO
import ro.jf.bk.account.sdk.AccountTransactionSdk
import ro.jf.bk.fund.api.model.CreateFundTransactionTO
import ro.jf.bk.fund.service.domain.FundRecord
import ro.jf.bk.fund.service.domain.FundTransaction
import java.util.*

const val METADATA_FUND_ID = "fundId"

class AccountTransactionAdapter(
    private val accountTransactionSdk: AccountTransactionSdk
) {
    suspend fun listTransactions(userId: UUID): List<FundTransaction> {
        return accountTransactionSdk.listTransactions(userId).map { it.toFundTransaction(userId) }
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction {
        val createAccountTransactionRequest = CreateAccountTransactionTO(
            dateTime = request.dateTime,
            records = request.records.map { record ->
                CreateAccountRecordTO(
                    accountId = record.accountId,
                    amount = record.amount,
                    metadata = mapOf(METADATA_FUND_ID to record.fundId.toString())
                )
            },
            metadata = emptyMap()
        )
        return accountTransactionSdk.createTransaction(userId, createAccountTransactionRequest)
            .toFundTransaction(userId)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return accountTransactionSdk.deleteTransaction(userId, transactionId)
    }

    private fun AccountTransactionTO.toFundTransaction(userId: UUID): FundTransaction =
        FundTransaction(
            id = this.id,
            userId = userId,
            dateTime = this.dateTime,
            records = this.records.map { recordTO ->
                FundRecord(
                    id = recordTO.id,
                    fundId = recordTO.fundId(),
                    accountId = recordTO.accountId,
                    amount = recordTO.amount
                )
            }
        )

    private fun AccountRecordTO.fundId(): UUID = metadata[METADATA_FUND_ID]
        ?.let(UUID::fromString)
        ?: error("Fund id not found in metadata")
}
